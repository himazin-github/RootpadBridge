/*
 * uinput_defs.h - uinput の ioctl/構造体定義 (NDK ヘッダ非依存の自己完結定義)
 *
 * bionic の linux/input.h は利用するが、linux/uinput.h は NDK のバージョンに
 * よって提供有無が不安定なため、UAPI と同一レイアウトの定義をここで行う。
 * ioctl 番号はカーネルの drivers/input/misc/uinput.c と一致している必要がある。
 */
#ifndef ROOTPAD_UINPUT_DEFS_H
#define ROOTPAD_UINPUT_DEFS_H

#include <linux/input.h>
#include <linux/types.h>
#include <sys/ioctl.h>

#ifndef EVIOCGRAB
#define EVIOCGRAB _IOW('E', 0x90, int)
#endif

#ifndef UI_DEV_CREATE
#define UI_DEV_CREATE _IO('U', 1)
#endif
#ifndef UI_DEV_DESTROY
#define UI_DEV_DESTROY _IO('U', 2)
#endif

#define RP_UI_SET_EVBIT  _IOW('U', 100, int)
#define RP_UI_SET_KEYBIT _IOW('U', 101, int)
#define RP_UI_SET_RELBIT _IOW('U', 102, int)
#define RP_UI_SET_ABSBIT _IOW('U', 103, int)

/* カーネルの struct uinput_setup と同一レイアウト (92 bytes) */
struct rp_uinput_setup {
    struct input_id id;
    __u8 name[80];
    __u32 ff_effects_max;
};

/* カーネルの struct uinput_abs_setup と同一レイアウト (28 bytes) */
struct rp_uinput_abs_setup {
    __u16 code;
    struct input_absinfo absinfo;
};

/* カーネルの struct uinput_user_dev と同一レイアウト (legacy path 用) */
#ifndef RP_ABS_CNT
#define RP_ABS_CNT 0x40
#endif
struct rp_uinput_user_dev {
    __u8 name[80];
    struct input_id id;
    __u32 ff_effects_max;
    __s32 absmax[RP_ABS_CNT];
    __s32 absmin[RP_ABS_CNT];
    __s32 absfuzz[RP_ABS_CNT];
    __s32 absflat[RP_ABS_CNT];
};

#define RP_UI_DEV_SETUP _IOW('U', 3, struct rp_uinput_setup)
#define RP_UI_ABS_SETUP _IOW('U', 4, struct rp_uinput_abs_setup)

#endif /* ROOTPAD_UINPUT_DEFS_H */
