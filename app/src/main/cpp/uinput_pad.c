/*
 * uinput_pad.c - /dev/uinput による仮想 Xbox 360 互換ゲームパッドの実装
 *
 * Xbox 360 有線パッド (045E:028E) と同じ識別情報・ボタン/軸構成でデバイスを
 * 生成する。Android の InputReader はこの ID に対して AOSP 同梱の
 * Vendor_045E_Product_028E.kl / .idc を参照するため、ゲームパッドとして
 * 分類される可能性が最も高い。実際の認識可否は送信側の診断画面で確認する。
 */
#include "uinput_pad.h"
#include "uinput_defs.h"
#include "rootpad.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

/* ボタンマスク bit → Linux BTN_* コード */
static const struct {
    uint32_t bit;
    int code;
} k_button_map[] = {
    { PAD_BIT_A,     BTN_SOUTH },
    { PAD_BIT_B,     BTN_EAST },
    { PAD_BIT_X,     BTN_NORTH },
    { PAD_BIT_Y,     BTN_WEST },
    { PAD_BIT_LB,    BTN_TL },
    { PAD_BIT_RB,    BTN_TR },
    { PAD_BIT_VIEW,  BTN_SELECT },
    { PAD_BIT_MENU,  BTN_START },
    { PAD_BIT_GUIDE, BTN_MODE },
    { PAD_BIT_LSP,   BTN_THUMBL },
    { PAD_BIT_RSP,   BTN_THUMBR },
};
#define K_BUTTON_COUNT ((int)(sizeof(k_button_map) / sizeof(k_button_map[0])))

static const int k_abs_axes[] = { ABS_X, ABS_Y, ABS_Z, ABS_RX, ABS_RY, ABS_RZ, ABS_HAT0X, ABS_HAT0Y };

/* 差分注入のための前回状態 */
static struct rp_pad_state g_last;
static int g_last_valid = 0;

static void emit(struct input_event *evs, int *n, int type, int code, int value)
{
    struct input_event *ev = &evs[(*n)++];
    memset(ev, 0, sizeof(*ev));
    ev->type = (uint16_t)type;
    ev->code = (uint16_t)code;
    ev->value = value;
}

static ssize_t write_all(int fd, const void *buf, size_t len)
{
    const uint8_t *p = (const uint8_t *)buf;
    size_t off = 0;
    while (off < len) {
        ssize_t w = write(fd, p + off, len - off);
        if (w < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        off += (size_t)w;
    }
    return (ssize_t)off;
}

int rp_pad_open(char *path_out, int path_len)
{
    static const char *candidates[] = { "/dev/uinput", "/dev/input/uinput" };
    for (size_t i = 0; i < sizeof(candidates) / sizeof(candidates[0]); i++) {
        int fd = open(candidates[i], O_WRONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd >= 0) {
            if (path_out && path_len > 0) {
                snprintf(path_out, (size_t)path_len, "%s", candidates[i]);
            }
            return fd;
        }
    }
    return -1;
}

static int setup_modern(int fd)
{
    struct rp_uinput_setup setup;
    memset(&setup, 0, sizeof(setup));
    setup.id.bustype = BUS_USB;
    setup.id.vendor = ROOTPAD_PAD_VENDOR;
    setup.id.product = ROOTPAD_PAD_PRODUCT;
    setup.id.version = ROOTPAD_PAD_VERSION;
    snprintf((char *)setup.name, sizeof(setup.name), "%s", ROOTPAD_PAD_NAME);
    setup.ff_effects_max = 0;
    if (ioctl(fd, RP_UI_DEV_SETUP, &setup) < 0) {
        return -1;
    }

    struct {
        int code;
        int lo, hi, fuzz, flat;
    } axes[] = {
        { ABS_X,     -32768, 32767, 16, 128 },
        { ABS_Y,     -32768, 32767, 16, 128 },
        { ABS_RX,    -32768, 32767, 16, 128 },
        { ABS_RY,    -32768, 32767, 16, 128 },
        { ABS_Z,     0, 255, 0, 0 },     /* LT */
        { ABS_RZ,    0, 255, 0, 0 },     /* RT */
        { ABS_HAT0X, -1, 1, 0, 0 },      /* 十字キー */
        { ABS_HAT0Y, -1, 1, 0, 0 },
    };
    for (size_t i = 0; i < sizeof(axes) / sizeof(axes[0]); i++) {
        struct rp_uinput_abs_setup abs;
        memset(&abs, 0, sizeof(abs));
        abs.code = (__u16)axes[i].code;
        abs.absinfo.minimum = axes[i].lo;
        abs.absinfo.maximum = axes[i].hi;
        abs.absinfo.fuzz = axes[i].fuzz;
        abs.absinfo.flat = axes[i].flat;
        abs.absinfo.value = (axes[i].lo < 0) ? 0 : 0;
        if (ioctl(fd, RP_UI_ABS_SETUP, &abs) < 0) {
            return -1;
        }
    }
    return 0;
}

static int setup_legacy(int fd)
{
    struct rp_uinput_user_dev udev;
    memset(&udev, 0, sizeof(udev));
    snprintf((char *)udev.name, sizeof(udev.name), "%s", ROOTPAD_PAD_NAME);
    udev.id.bustype = BUS_USB;
    udev.id.vendor = ROOTPAD_PAD_VENDOR;
    udev.id.product = ROOTPAD_PAD_PRODUCT;
    udev.id.version = ROOTPAD_PAD_VERSION;
    udev.absmin[ABS_X] = -32768;     udev.absmax[ABS_X] = 32767;
    udev.absmin[ABS_Y] = -32768;     udev.absmax[ABS_Y] = 32767;
    udev.absmin[ABS_RX] = -32768;    udev.absmax[ABS_RX] = 32767;
    udev.absmin[ABS_RY] = -32768;    udev.absmax[ABS_RY] = 32767;
    udev.absmin[ABS_Z] = 0;          udev.absmax[ABS_Z] = 255;
    udev.absmin[ABS_RZ] = 0;         udev.absmax[ABS_RZ] = 255;
    udev.absmin[ABS_HAT0X] = -1;     udev.absmax[ABS_HAT0X] = 1;
    udev.absmin[ABS_HAT0Y] = -1;     udev.absmax[ABS_HAT0Y] = 1;
    if (write_all(fd, &udev, sizeof(udev)) < 0) {
        return -1;
    }
    return 0;
}

int rp_pad_create(int fd, char *devnode_out, int devnode_len)
{
    /* キー/軸の割り当てを登録 */
    if (ioctl(fd, RP_UI_SET_EVBIT, EV_KEY) < 0) return -1;
    for (int i = 0; i < K_BUTTON_COUNT; i++) {
        if (ioctl(fd, RP_UI_SET_KEYBIT, k_button_map[i].code) < 0) return -1;
    }
    if (ioctl(fd, RP_UI_SET_EVBIT, EV_ABS) < 0) return -1;
    for (size_t i = 0; i < sizeof(k_abs_axes) / sizeof(k_abs_axes[0]); i++) {
        if (ioctl(fd, RP_UI_SET_ABSBIT, k_abs_axes[i]) < 0) return -1;
    }

    /* モダン API を優先し、非対応カーネルでは legacy API にフォールバック */
    if (setup_modern(fd) < 0) {
        if (setup_legacy(fd) < 0) {
            return -1;
        }
    }

    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        return -1;
    }

    /* uinput デバイスの登録は非同期。EventHub が拾えるまで少し待つ。 */
    for (int i = 0; i < 20; i++) {
        usleep(20 * 1000);
        int n = rp_pad_find_eventn();
        if (n >= 0) {
            if (devnode_out && devnode_len > 0) {
                snprintf(devnode_out, (size_t)devnode_len, "/dev/input/event%d", n);
            }
            return 0;
        }
    }
    /* eventN が特定できなくてもデバイス自体は生成済み (成功扱い) */
    if (devnode_out && devnode_len > 0) devnode_out[0] = '\0';
    return 0;
}

int rp_pad_find_eventn(void)
{
    DIR *d = opendir("/sys/class/input");
    if (!d) return -1;
    int found = -1;
    struct dirent *de;
    while ((de = readdir(d)) != NULL) {
        if (strncmp(de->d_name, "event", 5) != 0) continue;
        char path[256];
        snprintf(path, sizeof(path), "/sys/class/input/%s/device/name", de->d_name);
        FILE *f = fopen(path, "r");
        if (!f) continue;
        char name[128];
        if (fgets(name, sizeof(name), f)) {
            name[strcspn(name, "\r\n")] = '\0';
            if (strcmp(name, ROOTPAD_PAD_NAME) == 0) {
                found = atoi(de->d_name + 5);
                fclose(f);
                break;
            }
        }
        fclose(f);
    }
    closedir(d);
    return found;
}

static void flush_events(int fd, const struct input_event *evs, int n)
{
    if (n <= 0) return;
    /* SYN_REPORT でまとめる */
    struct input_event buf[64];
    int total = 0;
    for (int i = 0; i < n && total < 63; i++) buf[total++] = evs[i];
    if (total < 63) {
        memset(&buf[total], 0, sizeof(buf[0]));
        buf[total].type = EV_SYN;
        buf[total].code = SYN_REPORT;
        total++;
    }
    write_all(fd, buf, sizeof(buf[0]) * (size_t)total);
}

int rp_pad_apply(int fd, const struct rp_pad_state *st)
{
    struct input_event evs[48];
    int n = 0;
    struct rp_pad_state prev = g_last_valid ? g_last : (struct rp_pad_state){0};
    int16_t deflx = 0, defly = 0, defrx = 0, defry = 0;

    /* ボタン差分 */
    uint32_t diff = prev.buttons ^ st->buttons;
    for (int i = 0; i < K_BUTTON_COUNT; i++) {
        if (diff & k_button_map[i].bit) {
            emit(evs, &n, EV_KEY, k_button_map[i].code,
                 (st->buttons & k_button_map[i].bit) ? 1 : 0);
        }
    }

    /* スティック/トリガー/十字キー差分 */
    if (!g_last_valid || prev.lx != st->lx)  emit(evs, &n, EV_ABS, ABS_X, st->lx);
    if (!g_last_valid || prev.ly != st->ly)  emit(evs, &n, EV_ABS, ABS_Y, st->ly);
    if (!g_last_valid || prev.rx != st->rx)  emit(evs, &n, EV_ABS, ABS_RX, st->rx);
    if (!g_last_valid || prev.ry != st->ry)  emit(evs, &n, EV_ABS, ABS_RY, st->ry);
    if (!g_last_valid || prev.lt != st->lt)  emit(evs, &n, EV_ABS, ABS_Z, st->lt);
    if (!g_last_valid || prev.rt != st->rt)  emit(evs, &n, EV_ABS, ABS_RZ, st->rt);
    if (!g_last_valid || prev.hatx != st->hatx) emit(evs, &n, EV_ABS, ABS_HAT0X, st->hatx);
    if (!g_last_valid || prev.haty != st->haty) emit(evs, &n, EV_ABS, ABS_HAT0Y, st->haty);

    (void)deflx; (void)defly; (void)defrx; (void)defry;

    flush_events(fd, evs, n);
    g_last = *st;
    g_last_valid = 1;
    return 0;
}

void rp_pad_neutralize(int fd, struct rp_pad_state *cur)
{
    if (fd < 0) return;
    struct input_event evs[48];
    int n = 0;
    struct rp_pad_state prev = g_last_valid ? g_last : (struct rp_pad_state){0};

    if (prev.buttons != 0) {
        for (int i = 0; i < K_BUTTON_COUNT; i++) {
            if (prev.buttons & k_button_map[i].bit) {
                emit(evs, &n, EV_KEY, k_button_map[i].code, 0);
            }
        }
    }
    if (!g_last_valid || prev.lx != 0)  emit(evs, &n, EV_ABS, ABS_X, 0);
    if (!g_last_valid || prev.ly != 0)  emit(evs, &n, EV_ABS, ABS_Y, 0);
    if (!g_last_valid || prev.rx != 0)  emit(evs, &n, EV_ABS, ABS_RX, 0);
    if (!g_last_valid || prev.ry != 0)  emit(evs, &n, EV_ABS, ABS_RY, 0);
    if (!g_last_valid || prev.lt != 0)  emit(evs, &n, EV_ABS, ABS_Z, 0);
    if (!g_last_valid || prev.rt != 0)  emit(evs, &n, EV_ABS, ABS_RZ, 0);
    if (!g_last_valid || prev.hatx != 0) emit(evs, &n, EV_ABS, ABS_HAT0X, 0);
    if (!g_last_valid || prev.haty != 0) emit(evs, &n, EV_ABS, ABS_HAT0Y, 0);

    flush_events(fd, evs, n);
    g_last_valid = 0;
    if (cur) memset(cur, 0, sizeof(*cur));
}

void rp_pad_destroy(int fd)
{
    if (fd < 0) return;
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
}
