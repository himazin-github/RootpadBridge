/*
 * input_dev.h - 物理入力デバイス (/dev/input/event*) の列挙・取得・GRAB
 */
#ifndef ROOTPAD_INPUT_DEV_H
#define ROOTPAD_INPUT_DEV_H

#include <linux/input.h>

#define RP_KIND_OTHER  0
#define RP_KIND_KBD    1
#define RP_KIND_MOUSE  2

#define RP_MAX_DEVS 32

struct rp_input_dev {
    int fd;
    int eventn;              /* /dev/input/eventN */
    int kind;                /* RP_KIND_* */
    int grabbed;
    char name[256];
    char phys[256];
};

/*
 * /dev/input を走査して入力デバイスを列挙する。
 * exclude_eventn に >= 0 の値を渡すとその eventN を除外する
 * (自前の仮想パッドを grab 対象に含めないため)。
 * 戻り値は登録件数。
 */
int rp_input_scan(struct rp_input_dev *devs, int max, int exclude_eventn);

/* 指定 eventN を開いて情報を埋める。成功時 0。 */
int rp_input_open(int eventn, struct rp_input_dev *out);

/* grab=1 で独占 (物理入力の Android への直接到達を阻止)、0 で解除。 */
int rp_input_grab(struct rp_input_dev *dev, int grab);

void rp_input_close(struct rp_input_dev *dev);

/* 種別名 ("keyboard"/"mouse"/"other") */
const char *rp_kind_name(int kind);

#endif /* ROOTPAD_INPUT_DEV_H */
