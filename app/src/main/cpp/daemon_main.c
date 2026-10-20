/*
 * daemon_main.c - RootPad Bridge root daemon (rootpad_d)
 *
 * root 権限で動作し、以下を行う:
 *   1. /dev/uinput に Xbox 360 互換の仮想ゲームパッドを生成
 *   2. /dev/input/event* から物理キーボード/マウスの生イベントを取得
 *      (必要に応じて EVIOCGRAB で Android への直接到達を阻止)
 *   3. abstract Unix ソケット経由でアプリに生イベントを転送し、
 *      アプリから受信したゲームパッド状態を uinput に注入
 *   4. 定期監視: 仮想デバイスの消失検知 / クライアント死活監視 (watchdog)
 *
 * プロトコル (テキスト行, \n 区切り; Protocol.kt と対):
 *   app → daemon:
 *     HELLO | UINPUT | KLINSTALL | PADCREATE | PADDESTROY | SELFTEST
 *     GRAB <0|1> | PAD <mask> <lx> <ly> <rx> <ry> <lt> <rt> <hatx> <haty>
 *     LIST | PING | QUIT
 *     ※ PAD は高頻度のため応答なし (片方向)
 *   daemon → app:
 *     INFO <key>=<value> | READY proto=<n> pid=<n>
 *     OK <cmd> ... | ERR <cmd> errno=<n> msg=<text>
 *     EV <eventn> <type> <code> <value>
 *     DEVADD <eventn> <kind> <name> | DEVDEL <eventn>
 *     GONE <reason>
 */
#include "rootpad.h"
#include "uinput_pad.h"
#include "input_dev.h"
#include "usock.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#define OUTBUF_MAX 65536
#define INBUF_MAX 8192
#define WATCHDOG_MS 8000
#define NOCLIENT_EXIT_MS 120000

static char g_out[OUTBUF_MAX];
static size_t g_out_len = 0;
static int g_client = -1;
static char g_inbuf[INBUF_MAX];
static size_t g_inlen = 0;

static int g_pad_fd = -1;
static int g_pad_eventn = -1;
static int g_grab = 0;
static struct rp_input_dev g_devs[RP_MAX_DEVS];
static int g_ndev = 0;
static volatile sig_atomic_t g_quit = 0;
static char g_uinput_path[64];
static int g_uinput_ok = 0;
static char g_sockname[128] = ROOTPAD_SOCKET_NAME;

static long now_ms(void)
{
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long)(ts.tv_sec * 1000 + ts.tv_nsec / 1000000);
}

static void on_signal(int sig) { (void)sig; g_quit = 1; }

/* ---------------- 出力バッファ ---------------- */

static void queue_line(const char *line, size_t len, int droppable)
{
    if (g_client < 0) return;
    if (g_out_len + len + 1 > OUTBUF_MAX) {
        if (droppable) return;      /* 過負荷時は EV 転送を間引く */
        /* 致命的でない行は破棄して継続 */
        return;
    }
    memcpy(g_out + g_out_len, line, len);
    g_out_len += len;
    g_out[g_out_len++] = '\n';
}

static void qf(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
static void qf(const char *fmt, ...)
{
    char buf[1024];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    if (n > 0) queue_line(buf, (size_t)(n < (int)sizeof(buf) ? n : (int)sizeof(buf) - 1), 0);
}

static void qf_droppable(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
static void qf_droppable(const char *fmt, ...)
{
    char buf[256];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    if (n > 0) queue_line(buf, (size_t)(n < (int)sizeof(buf) ? n : (int)sizeof(buf) - 1), 1);
}

static void flush_out(void)
{
    size_t off = 0;
    while (off < g_out_len) {
        ssize_t w = write(g_client, g_out + off, g_out_len - off);
        if (w < 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) break;
            return; /* ピア消失。watchdog/読取側で後始末される */
        }
        off += (size_t)w;
    }
    if (off > 0 && off < g_out_len) {
        memmove(g_out, g_out + off, g_out_len - off);
    }
    g_out_len -= off;
}

/* ---------------- システム情報 ---------------- */

static void read_first_line(const char *path, char *out, size_t len)
{
    out[0] = '\0';
    FILE *f = fopen(path, "r");
    if (!f) return;
    if (fgets(out, (int)len, f)) {
        out[strcspn(out, "\r\n")] = '\0';
    }
    fclose(f);
}

static void report_sysinfo(void)
{
    char buf[512];
    read_first_line("/proc/version", buf, sizeof(buf));
    /* "Linux version X.Y.Z-..." → カーネルバージョンのみ抜き出す */
    char *p = strstr(buf, "version ");
    if (p) {
        p += 8;
        char ver[96];
        size_t i = 0;
        while (p[i] && p[i] != ' ' && i < sizeof(ver) - 1) { ver[i] = p[i]; i++; }
        ver[i] = '\0';
        qf("INFO kernel=%s", ver);
    } else {
        qf("INFO kernel=unknown");
    }

    int enforce = -1;
    FILE *f = fopen("/sys/fs/selinux/enforce", "r");
    if (f) {
        if (fscanf(f, "%d", &enforce) != 1) enforce = -1;
        fclose(f);
    }
    if (enforce >= 0) {
        qf("INFO selinux=%s", enforce ? "enforcing" : "permissive");
    } else {
        qf("INFO selinux=disabled_or_unknown");
    }

    read_first_line("/proc/self/attr/current", buf, sizeof(buf));
    qf("INFO ctx=%s", buf[0] ? buf : "unknown");

    qf("INFO miscuinput=%d", access("/sys/class/misc/uinput", F_OK) == 0 ? 1 : 0);
}

static int probe_uinput(void)
{
    char path[64];
    int fd = rp_pad_open(path, sizeof(path));
    if (fd < 0) {
        g_uinput_ok = 0;
        g_uinput_path[0] = '\0';
        return -1;
    }
    g_uinput_ok = 1;
    snprintf(g_uinput_path, sizeof(g_uinput_path), "%s", path);
    close(fd);
    return 0;
}

/* ---------------- Xbox 用キーレイアウト/IDC の導入 (Root) ---------------- */

static const char k_kl_content[] =
    "# Generated by RootPad Bridge\n"
    "# Xbox 360 compatible virtual gamepad (045E:028E)\n"
    "key 304   BUTTON_A\n"
    "key 305   BUTTON_B\n"
    "key 307   BUTTON_X\n"
    "key 308   BUTTON_Y\n"
    "key 310   BUTTON_L1\n"
    "key 311   BUTTON_R1\n"
    "key 314   BUTTON_SELECT\n"
    "key 315   BUTTON_START\n"
    "key 316   BUTTON_MODE\n"
    "key 317   BUTTON_THUMBL\n"
    "key 318   BUTTON_THUMBR\n"
    "axis 0x00 AXIS_X\n"
    "axis 0x01 AXIS_Y\n"
    "axis 0x02 AXIS_LTRIGGER\n"
    "axis 0x03 AXIS_RX\n"
    "axis 0x04 AXIS_RY\n"
    "axis 0x05 AXIS_RTRIGGER\n"
    "axis 0x10 AXIS_HAT_X\n"
    "axis 0x11 AXIS_HAT_Y\n";

static const char k_idc_content[] =
    "# Generated by RootPad Bridge\n"
    "deviceType = gamepad\n";

static int write_kl_file(const char *path, const char *content)
{
    /* 既存ファイルと同一なら何もしない */
    FILE *f = fopen(path, "r");
    if (f) {
        char buf[2048];
        size_t n = fread(buf, 1, sizeof(buf) - 1, f);
        fclose(f);
        buf[n] = '\0';
        if (strcmp(buf, content) == 0) return 1; /* already */
    }
    f = fopen(path, "w");
    if (!f) return 0;
    size_t len = strlen(content);
    size_t w = fwrite(content, 1, len, f);
    fclose(f);
    if (w != len) return 0;
    chmod(path, 0644);
    return 2; /* installed */
}

static int install_keylayout(void)
{
    mkdir("/data/system", 0755);
    mkdir("/data/system/devices", 0755);
    mkdir("/data/system/devices/keylayout", 0755);
    mkdir("/data/system/devices/idc", 0755);

    int kl = write_kl_file("/data/system/devices/keylayout/Vendor_045E_Product_028E.kl", k_kl_content);
    int idc = write_kl_file("/data/system/devices/idc/Vendor_045E_Product_028E.idc", k_idc_content);
    if (kl == 0 || idc == 0) return 0;
    return 1;
}

/* ---------------- デバイス管理 ---------------- */

static void close_all_devs(void)
{
    for (int i = 0; i < g_ndev; i++) rp_input_close(&g_devs[i]);
    g_ndev = 0;
}

/* 一覧を再構築し、増減を通知する。
 * 既知デバイスは fd/grab 状態を維持し、新規のみ open、消失のみ close する。 */
static void rescan_devs(void)
{
    struct rp_input_dev fresh[RP_MAX_DEVS];
    int nf = rp_input_scan(fresh, RP_MAX_DEVS, g_pad_eventn);

    struct rp_input_dev merged[RP_MAX_DEVS];
    int used_fresh[RP_MAX_DEVS] = {0};
    int nm = 0;

    /* 既存デバイスを維持し、新規デバイスを追加する */
    for (int i = 0; i < nf && nm < RP_MAX_DEVS; i++) {
        int existed = -1;
        for (int j = 0; j < g_ndev; j++) {
            if (g_devs[j].eventn == fresh[i].eventn) { existed = j; break; }
        }
        if (existed >= 0) {
            merged[nm++] = g_devs[existed];      /* open 済みのものを維持 */
            used_fresh[i] = 1;
            rp_input_close(&fresh[i]);           /* 新規 open した分は閉じる */
        }
    }
    for (int i = 0; i < nf && nm < RP_MAX_DEVS; i++) {
        if (used_fresh[i]) continue;
        if (g_grab) rp_input_grab(&fresh[i], 1);
        qf("DEVADD %d %s %s", fresh[i].eventn, rp_kind_name(fresh[i].kind), fresh[i].name);
        merged[nm++] = fresh[i];
    }

    /* 消失したデバイスを検出して解放 (押下状態の残りを防ぐため必ず通知) */
    for (int j = 0; j < g_ndev; j++) {
        int still = 0;
        for (int i = 0; i < nf; i++) {
            if (!used_fresh[i] && fresh[i].eventn == g_devs[j].eventn) { still = 1; break; }
            if (used_fresh[i]) {
                /* 維持されたデバイスは merged に含まれる */
            }
        }
        for (int m = 0; m < nm && !still; m++) {
            if (merged[m].eventn == g_devs[j].eventn) still = 1;
        }
        if (!still) {
            qf("DEVDEL %d", g_devs[j].eventn);
            rp_input_close(&g_devs[j]);
        }
    }

    memcpy(g_devs, merged, sizeof(struct rp_input_dev) * (size_t)nm);
    g_ndev = nm;
}

static void set_grab(int on)
{
    g_grab = on ? 1 : 0;
    for (int i = 0; i < g_ndev; i++) {
        int want = g_grab ? 1 : 0;
        if (g_devs[i].grabbed != want) rp_input_grab(&g_devs[i], want);
    }
}

static void ungrab_all(void)
{
    for (int i = 0; i < g_ndev; i++) {
        if (g_devs[i].grabbed) rp_input_grab(&g_devs[i], 0);
    }
    g_grab = 0;
}

/* ---------------- パッド操作 ---------------- */

static void do_padcreate(void)
{
    if (g_pad_fd >= 0) {
        qf("OK PADCREATE name=%s eventn=%d (existing)", ROOTPAD_PAD_NAME, g_pad_eventn);
        return;
    }
    char path[64];
    int fd = rp_pad_open(path, sizeof(path));
    if (fd < 0) {
        qf("ERR PADCREATE errno=%d msg=uinput_open_failed", errno);
        return;
    }
    char node[64] = "";
    if (rp_pad_create(fd, node, sizeof(node)) < 0) {
        int e = errno;
        close(fd);
        qf("ERR PADCREATE errno=%d msg=uinput_create_failed", e);
        return;
    }
    g_pad_fd = fd;
    g_pad_eventn = rp_pad_find_eventn();
    qf("OK PADCREATE name=%s eventn=%d node=%s uinput=%s",
       ROOTPAD_PAD_NAME, g_pad_eventn, node[0] ? node : "unknown", path);
    /* 仮想デバイスは grab 対象外として扱うため再走査 */
    rescan_devs();
}

static void do_paddestroy(void)
{
    if (g_pad_fd >= 0) {
        rp_pad_neutralize(g_pad_fd, NULL);
        rp_pad_destroy(g_pad_fd);
        g_pad_fd = -1;
        g_pad_eventn = -1;
    }
    qf("OK PADDESTROY");
}

static void do_selftest(void)
{
    char path[64];
    int fd = rp_pad_open(path, sizeof(path));
    if (fd < 0) {
        qf("ERR SELFTEST errno=%d msg=uinput_open_failed", errno);
        return;
    }
    char node[64] = "";
    if (rp_pad_create(fd, node, sizeof(node)) < 0) {
        int e = errno;
        close(fd);
        qf("ERR SELFTEST errno=%d msg=uinput_create_failed", e);
        return;
    }
    struct rp_pad_state st;
    memset(&st, 0, sizeof(st));
    st.buttons = PAD_BIT_A;
    st.lx = 1234; st.ry = -5678; st.lt = 200; st.hatx = 1;
    rp_pad_apply(fd, &st);       /* 書き込み可能なことを確認 */
    rp_pad_neutralize(fd, &st);
    rp_pad_destroy(fd);
    qf("OK SELFTEST uinput=%s eventn=%d wr=ok", path, rp_pad_find_eventn() >= 0 ? 1 : 0);
}

/* ---------------- コマンド処理 ---------------- */

static void handle_line(char *line)
{
    /* 末尾の空白除去 */
    size_t len = strlen(line);
    while (len > 0 && (line[len - 1] == '\r' || line[len - 1] == ' ')) line[--len] = '\0';
    if (len == 0) return;

    char *cmd = line;
    char *arg = NULL;
    char *sp = strchr(line, ' ');
    if (sp) { *sp = '\0'; arg = sp + 1; }

    if (strcmp(cmd, "HELLO") == 0) {
        qf("OK HELLO proto=%d", ROOTPAD_PROTO_VERSION);
    } else if (strcmp(cmd, "UINPUT") == 0) {
        int r = probe_uinput();
        if (r == 0) qf("OK UINPUT path=%s", g_uinput_path);
        else qf("ERR UINPUT errno=%d msg=open_failed", errno);
    } else if (strcmp(cmd, "KLINSTALL") == 0) {
        if (install_keylayout()) qf("OK KLINSTALL path=/data/system/devices/keylayout/Vendor_045E_Product_028E.kl");
        else qf("ERR KLINSTALL errno=%d msg=write_failed", errno);
    } else if (strcmp(cmd, "PADCREATE") == 0) {
        do_padcreate();
    } else if (strcmp(cmd, "PADDESTROY") == 0) {
        do_paddestroy();
    } else if (strcmp(cmd, "SELFTEST") == 0) {
        do_selftest();
    } else if (strcmp(cmd, "GRAB") == 0) {
        int on = arg ? atoi(arg) : 0;
        set_grab(on);
        qf("OK GRAB n=%d", g_grab);
    } else if (strcmp(cmd, "PAD") == 0) {
        if (g_pad_fd < 0 || !arg) return; /* パッド未生成時は黙って破棄 */
        unsigned int mask = 0;
        int lx, ly, rx, ry, lt, rt, hx, hy;
        if (sscanf(arg, "%u %d %d %d %d %d %d %d %d",
                   &mask, &lx, &ly, &rx, &ry, &lt, &rt, &hx, &hy) == 9) {
            struct rp_pad_state st;
            memset(&st, 0, sizeof(st));
            st.buttons = mask & 0x7ffu;
            st.lx = (int16_t)(lx < -32768 ? -32768 : (lx > 32767 ? 32767 : lx));
            st.ly = (int16_t)(ly < -32768 ? -32768 : (ly > 32767 ? 32767 : ly));
            st.rx = (int16_t)(rx < -32768 ? -32768 : (rx > 32767 ? 32767 : rx));
            st.ry = (int16_t)(ry < -32768 ? -32768 : (ry > 32767 ? 32767 : ry));
            st.lt = (uint8_t)(lt < 0 ? 0 : (lt > 255 ? 255 : lt));
            st.rt = (uint8_t)(rt < 0 ? 0 : (rt > 255 ? 255 : rt));
            st.hatx = (int8_t)(hx < -1 ? -1 : (hx > 1 ? 1 : hx));
            st.haty = (int8_t)(hy < -1 ? -1 : (hy > 1 ? 1 : hy));
            rp_pad_apply(g_pad_fd, &st);
        }
    } else if (strcmp(cmd, "LIST") == 0) {
        rescan_devs();
        for (int i = 0; i < g_ndev; i++) {
            qf("DEV %d %s grab=%d %s", g_devs[i].eventn,
               rp_kind_name(g_devs[i].kind), g_devs[i].grabbed, g_devs[i].name);
        }
        qf("OK LIST n=%d pad=%d", g_ndev, g_pad_fd >= 0 ? 1 : 0);
    } else if (strcmp(cmd, "PING") == 0) {
        qf("OK PONG");
    } else if (strcmp(cmd, "QUIT") == 0) {
        qf("OK BYE");
        g_quit = 1;
    } else {
        qf("ERR UNKNOWN cmd=%s", cmd);
    }
}

/* ---------------- 後始末 ---------------- */

static void cleanup(void)
{
    if (g_pad_fd >= 0) {
        rp_pad_neutralize(g_pad_fd, NULL);
        rp_pad_destroy(g_pad_fd);
        g_pad_fd = -1;
    }
    ungrab_all();
    close_all_devs();
}

/* ---------------- main ---------------- */

static void print_usage(void)
{
    fprintf(stderr,
        "rootpad_d - RootPad Bridge daemon (must run as root)\n"
        "  --sock <name>   abstract socket name (default: %s)\n"
        "  --selftest      probe uinput, create+destroy a test pad, exit\n"
        "  --version       print version and exit\n",
        ROOTPAD_SOCKET_NAME);
}

int main(int argc, char **argv)
{
    int selftest = 0;
    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--sock") == 0 && i + 1 < argc) {
            snprintf(g_sockname, sizeof(g_sockname), "%s", argv[++i]);
        } else if (strcmp(argv[i], "--selftest") == 0) {
            selftest = 1;
        } else if (strcmp(argv[i], "--version") == 0) {
            fprintf(stderr, "rootpad_d proto=%d\n", ROOTPAD_PROTO_VERSION);
            return 0;
        } else {
            print_usage();
            return 2;
        }
    }

    signal(SIGPIPE, SIG_IGN);
    signal(SIGTERM, on_signal);
    signal(SIGINT, on_signal);

    if (geteuid() != 0) {
        fprintf(stderr, "rootpad_d: not running as root (euid=%d)\n", geteuid());
        return 3;
    }

    if (selftest) {
        char path[64], node[64] = "";
        int fd = rp_pad_open(path, sizeof(path));
        if (fd < 0) {
            fprintf(stderr, "selftest: uinput open failed errno=%d\n", errno);
            return 4;
        }
        if (rp_pad_create(fd, node, sizeof(node)) < 0) {
            fprintf(stderr, "selftest: pad create failed errno=%d\n", errno);
            close(fd);
            return 5;
        }
        rp_pad_destroy(fd);
        fprintf(stderr, "selftest: ok uinput=%s\n", path);
        return 0;
    }

    int lfd = rp_usock_listen(g_sockname);
    if (lfd < 0) {
        fprintf(stderr, "rootpad_d: listen failed errno=%d\n", errno);
        return 6;
    }

    int inofd = inotify_init1(IN_NONBLOCK | IN_CLOEXEC);
    if (inofd >= 0) {
        inotify_add_watch(inofd, "/dev/input", IN_CREATE | IN_DELETE | IN_ATTRIB);
    }

    report_sysinfo();
    int pr = probe_uinput();
    if (pr == 0) qf("INFO uinput=ok path=%s", g_uinput_path);
    else qf("INFO uinput=fail errno=%d", errno);
    qf("READY proto=%d pid=%d", ROOTPAD_PROTO_VERSION, (int)getpid());
    rescan_devs();

    long start = now_ms();
    long last_activity = start;
    long last_devscan = start;
    long last_padcheck = start;

    while (!g_quit) {
        struct pollfd pfds[4 + RP_MAX_DEVS];
        int nfd = 0;
        pfds[nfd].fd = lfd;                 pfds[nfd].events = POLLIN; nfd++;
        pfds[nfd].fd = g_client;            pfds[nfd].events = POLLIN |
                                        (g_out_len > 0 ? POLLOUT : 0); nfd++;
        pfds[nfd].fd = inofd;               pfds[nfd].events = POLLIN; nfd++;
        int devbase = nfd;
        for (int i = 0; i < g_ndev; i++) {
            pfds[nfd].fd = g_devs[i].fd;
            pfds[nfd].events = POLLIN;
            nfd++;
        }

        int r = poll(pfds, (nfds_t)nfd, 100);
        if (r < 0) {
            if (errno == EINTR) continue;
            break;
        }

        /* 新規接続 */
        if (pfds[0].revents & POLLIN) {
            int cfd = rp_usock_accept(lfd);
            if (cfd >= 0) {
                if (g_client >= 0) close(g_client);
                g_client = cfd;
                g_inlen = 0;
                g_out_len = 0;
                last_activity = now_ms();
                qf("INFO client_connected=1");
            }
        }

        /* クライアント入力 */
        if (g_client >= 0 && (pfds[1].revents & (POLLIN | POLLHUP | POLLERR))) {
            char tmp[2048];
            ssize_t n = read(g_client, tmp, sizeof(tmp));
            if (n == 0 || (n < 0 && errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR)) {
                /* クライアント切断 → 後始末して終了 (次回起動で再初期化) */
                qf("INFO client_disconnected=1");
                flush_out();
                g_quit = 1;
            } else if (n > 0) {
                last_activity = now_ms();
                for (ssize_t i = 0; i < n; i++) {
                    if (tmp[i] == '\n') {
                        g_inbuf[g_inlen] = '\0';
                        handle_line(g_inbuf);
                        g_inlen = 0;
                    } else if (g_inlen + 1 < INBUF_MAX) {
                        g_inbuf[g_inlen++] = tmp[i];
                    } else {
                        g_inlen = 0; /* 異常行は破棄 */
                    }
                }
            }
        }
        if (g_client >= 0 && g_out_len > 0 && (pfds[1].revents & POLLOUT)) {
            flush_out();
        }

        /* inotify: 入力デバイスの増減 */
        if (inofd >= 0 && (pfds[2].revents & POLLIN)) {
            char ibuf[4096] __attribute__((aligned(__alignof__(struct inotify_event))));
            ssize_t n = read(inofd, ibuf, sizeof(ibuf));
            (void)n;
            long t = now_ms();
            if (t - last_devscan > 200) {
                last_devscan = t;
                rescan_devs();
            }
        }

        /* 入力イベントの転送 (grab 中の端末のみが意味を持つ) */
        for (int i = 0; i < g_ndev; i++) {
            struct pollfd *p = &pfds[devbase + i];
            if (!(p->revents & (POLLIN | POLLHUP | POLLERR))) continue;
            struct input_event evs[64];
            ssize_t n = read(g_devs[i].fd, evs, sizeof(evs));
            if (n < 0) {
                if (errno == EAGAIN || errno == EWOULDBLOCK || errno == EINTR) continue;
                /* 読めなくなった端末は閉じて一覧から外す */
                qf("DEVDEL %d", g_devs[i].eventn);
                rp_input_close(&g_devs[i]);
                continue;
            }
            int cnt = (int)(n / (ssize_t)sizeof(struct input_event));
            for (int k = 0; k < cnt; k++) {
                if (evs[k].type == EV_KEY || evs[k].type == EV_REL) {
                    qf_droppable("EV %d %d %d %d", g_devs[i].eventn,
                                 evs[k].type, evs[k].code, evs[k].value);
                }
            }
        }

        /* 定期再走査: USB の抜き差しで inotify を取りこぼしても
         * 2 秒以内にキーボード/マウスの増減を反映させる。 */
        long tnow = now_ms();
        if (tnow - last_devscan > 2000) {
            last_devscan = tnow;
            rescan_devs();
        }

        /* 仮想パッド消失の監視 (1 秒間隔) */
        if (g_pad_fd >= 0 && tnow - last_padcheck > 1000) {
            last_padcheck = tnow;
            int n = rp_pad_find_eventn();
            if (n < 0) {
                qf("GONE pad node_removed");
                rp_pad_destroy(g_pad_fd);
                g_pad_fd = -1;
                g_pad_eventn = -1;
            } else if (n != g_pad_eventn) {
                g_pad_eventn = n;
            }
        }

        /* watchdog: クライアント無通信 → 入力を解除して終了 */
        if (g_client >= 0 && tnow - last_activity > WATCHDOG_MS) {
            qf("INFO watchdog_timeout=1");
            flush_out();
            g_quit = 1;
        }
        /* クライアント未接続が長すぎる場合も終了 */
        if (g_client < 0 && tnow - start > NOCLIENT_EXIT_MS) {
            g_quit = 1;
        }

        if (g_out_len > 0 && g_client >= 0) flush_out();
    }

    cleanup();
    if (g_client >= 0) {
        flush_out();
        close(g_client);
        g_client = -1;
    }
    if (inofd >= 0) close(inofd);
    close(lfd);
    return 0;
}
