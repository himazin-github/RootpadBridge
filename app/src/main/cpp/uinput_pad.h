/*
 * uinput_pad.h - 仮想 Xbox 360 互換ゲームパッド (uinput)
 */
#ifndef ROOTPAD_UINPUT_PAD_H
#define ROOTPAD_UINPUT_PAD_H

#include <stdint.h>

struct rp_pad_state {
    uint32_t buttons; /* PAD_BIT_* の組み合わせ */
    int16_t lx, ly;   /* 左スティック  -32768..32767 (Y: 下が正) */
    int16_t rx, ry;   /* 右スティック  -32768..32767 */
    uint8_t lt, rt;   /* トリガー 0..255 */
    int8_t hatx, haty; /* 十字キー -1..1 (0=中立) */
};

/*
 * uinput デバイスを開く。成功時に fd を返す。path_out に実際に開いた
 * ノードパスを格納する。失敗時は -1 (errno 有効)。
 */
int rp_pad_open(char *path_out, int path_len);

/* 仮想パッドを生成する。成功時 0。devnode_out に eventN を格納可能。 */
int rp_pad_create(int fd, char *devnode_out, int devnode_len);

/* 状態の差分をイベントとして注入する (SYN_REPORT 付き)。成功時 0。 */
int rp_pad_apply(int fd, const struct rp_pad_state *st);

/* 全入力を見積もって中立状態に戻す。 */
void rp_pad_neutralize(int fd, struct rp_pad_state *cur);

/* デバイスを破棄して fd を閉じる。 */
void rp_pad_destroy(int fd);

/* システムから見つかった eventN を名前で探す (-1 なら未検出)。 */
int rp_pad_find_eventn(void);

#endif /* ROOTPAD_UINPUT_PAD_H */
