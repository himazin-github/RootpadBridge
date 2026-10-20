/*
 * rootpad.h - RootPad Bridge 共通定義
 *
 * デーモン(rootpad_d)とKotlin側(Protocol.kt)で共有するプロトコル定数。
 * ボタンマスクのビット割り当ては両者が完全一致している必要がある。
 */
#ifndef ROOTPAD_COMMON_H
#define ROOTPAD_COMMON_H

#define ROOTPAD_PROTO_VERSION 1

/* Xbox 360 有線パッド互換の識別情報 (AOSP 既定のキーレイアウト/IDCが
 * Vendor_045E_Product_028E 名で同梱されているため、この ID を使用する) */
#define ROOTPAD_PAD_NAME    "Microsoft X-Box 360 pad"
#define ROOTPAD_PAD_VENDOR  0x045e
#define ROOTPAD_PAD_PRODUCT 0x028e
#define ROOTPAD_PAD_VERSION 0x0110
#define ROOTPAD_PAD_PHYS    "usb-RootPadBridge/virtual"

/* PAD メッセージのボタンマスク・ビット (Protocol.kt と同一) */
#define PAD_BIT_A        (1u << 0)  /* BTN_SOUTH (304) */
#define PAD_BIT_B        (1u << 1)  /* BTN_EAST  (305) */
#define PAD_BIT_X        (1u << 2)  /* BTN_NORTH (307) */
#define PAD_BIT_Y        (1u << 3)  /* BTN_WEST  (308) */
#define PAD_BIT_LB       (1u << 4)  /* BTN_TL    (310) */
#define PAD_BIT_RB       (1u << 5)  /* BTN_TR    (311) */
#define PAD_BIT_VIEW     (1u << 6)  /* BTN_SELECT(314) */
#define PAD_BIT_MENU     (1u << 7)  /* BTN_START (315) */
#define PAD_BIT_GUIDE    (1u << 8)  /* BTN_MODE  (316) */
#define PAD_BIT_LSP      (1u << 9)  /* BTN_THUMBL(317) */
#define PAD_BIT_RSP      (1u << 10) /* BTN_THUMBR(318) */

#define ROOTPAD_SOCKET_NAME "rootpad.bridge.daemon"

#endif /* ROOTPAD_COMMON_H */
