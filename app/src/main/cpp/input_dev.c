/*
 * input_dev.c - /dev/input/event* の列挙・分類・GRAB
 *
 * 分類は capabilities ビットから機械的に行う:
 *  - キーボード: EV_KEY があり KEY_A..KEY_Z が全て設定されている
 *  - マウス:     EV_REL の REL_X/REL_Y と EV_KEY の BTN_LEFT がある
 * 電源ボタン(gpio-keys 等)やタッチスクリーンは分類されず grab 対象外。
 */
#include "input_dev.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#define BITS_PER_LONG (sizeof(unsigned long) * 8)
#define NLONGS(x) (((x) + BITS_PER_LONG - 1) / BITS_PER_LONG)
#define TEST_BIT(bit, array) ((array[(bit) / BITS_PER_LONG] >> ((bit) % BITS_PER_LONG)) & 1UL)

static int has_letters(const unsigned long *keybit)
{
    /* KEY_A(30)..KEY_L(38), KEY_Z(44)..KEY_M(50) が揃っていればQWERTYキーボード */
    for (int c = 30; c <= 38; c++) {
        if (!TEST_BIT(c, keybit)) return 0;
    }
    for (int c = 44; c <= 50; c++) {
        if (!TEST_BIT(c, keybit)) return 0;
    }
    return 1;
}

static int looks_like_ours(const char *name)
{
    /* 自前の仮想デバイスは grab 対象にしない */
    if (strstr(name, "RootPad") != NULL) return 1;
    if (strstr(name, "X-Box 360 pad") != NULL) return 1;
    if (strstr(name, "Xbox 360") != NULL) return 1;
    return 0;
}

int rp_input_open(int eventn, struct rp_input_dev *out)
{
    memset(out, 0, sizeof(*out));
    out->fd = -1;
    char path[64];
    snprintf(path, sizeof(path), "/dev/input/event%d", eventn);
    int fd = open(path, O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) return -1;

    out->fd = fd;
    out->eventn = eventn;

    if (ioctl(fd, EVIOCGNAME(sizeof(out->name) - 1), out->name) < 0) {
        snprintf(out->name, sizeof(out->name), "event%d", eventn);
    }
    if (ioctl(fd, EVIOCGPHYS(sizeof(out->phys) - 1), out->phys) < 0) {
        out->phys[0] = '\0';
    }

    if (looks_like_ours(out->name)) {
        out->kind = RP_KIND_OTHER;
        return 0;
    }

    unsigned long evbit[NLONGS(EV_MAX + 1)];
    unsigned long keybit[NLONGS(KEY_MAX + 1)];
    unsigned long relbit[NLONGS(REL_MAX + 1)];
    memset(evbit, 0, sizeof(evbit));
    memset(keybit, 0, sizeof(keybit));
    memset(relbit, 0, sizeof(relbit));

    if (ioctl(fd, EVIOCGBIT(0, sizeof(evbit)), evbit) < 0) {
        out->kind = RP_KIND_OTHER;
        return 0;
    }

    /* EVIOCGBIT の戻り値は「書き込まれたバイト数」(成功時 >= 0) であり、
     * 0 とは限らない。そのため成功判定は必ず >= 0 で行う。 */
    int kind = RP_KIND_OTHER;
    if (TEST_BIT(EV_KEY, evbit)) {
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keybit)), keybit) >= 0) {
            if (has_letters(keybit)) {
                kind = RP_KIND_KBD;
            }
        }
    }
    if (kind == RP_KIND_OTHER && TEST_BIT(EV_REL, evbit)) {
        if (ioctl(fd, EVIOCGBIT(EV_REL, sizeof(relbit)), relbit) >= 0 &&
            TEST_BIT(REL_X, relbit) && TEST_BIT(REL_Y, relbit)) {
            unsigned long kbtn[NLONGS(KEY_MAX + 1)];
            memset(kbtn, 0, sizeof(kbtn));
            if (TEST_BIT(EV_KEY, evbit) &&
                ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(kbtn)), kbtn) >= 0 &&
                TEST_BIT(BTN_LEFT, kbtn)) {
                kind = RP_KIND_MOUSE;
            }
        }
    }
    out->kind = kind;
    return 0;
}

int rp_input_scan(struct rp_input_dev *devs, int max, int exclude_eventn)
{
    DIR *d = opendir("/dev/input");
    if (!d) return 0;
    int count = 0;
    struct dirent *de;
    while ((de = readdir(d)) != NULL && count < max) {
        if (strncmp(de->d_name, "event", 5) != 0) continue;
        const char *num = de->d_name + 5;
        if (*num == '\0') continue;
        char *end = NULL;
        long n = strtol(num, &end, 10);
        if (end == NULL || *end != '\0' || n < 0 || n > 65535) continue;
        if ((int)n == exclude_eventn) continue;

        struct rp_input_dev dev;
        if (rp_input_open((int)n, &dev) == 0) {
            if (dev.kind != RP_KIND_OTHER) {
                devs[count++] = dev;
            } else {
                rp_input_close(&dev);
            }
        }
    }
    closedir(d);
    return count;
}

int rp_input_grab(struct rp_input_dev *dev, int grab)
{
    if (dev->fd < 0) return -1;
    if (ioctl(dev->fd, EVIOCGRAB, grab ? 1 : 0) < 0) return -1;
    dev->grabbed = grab ? 1 : 0;
    return 0;
}

void rp_input_close(struct rp_input_dev *dev)
{
    if (dev->fd >= 0) {
        if (dev->grabbed) ioctl(dev->fd, EVIOCGRAB, 0);
        close(dev->fd);
    }
    dev->fd = -1;
    dev->grabbed = 0;
}

const char *rp_kind_name(int kind)
{
    switch (kind) {
        case RP_KIND_KBD: return "kbd";
        case RP_KIND_MOUSE: return "mouse";
        default: return "other";
    }
}
