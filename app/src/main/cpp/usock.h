/*
 * usock.h - abstract ネームスペースの Unix ドメインソケット (サーバ側)
 */
#ifndef ROOTPAD_USOCK_H
#define ROOTPAD_USOCK_H

/* リッスン用ソケットを作成する。失敗時 -1。 */
int rp_usock_listen(const char *name);

/* 接続を受け入れる (ブロッキング可)。失敗時 -1。 */
int rp_usock_accept(int lfd);

#endif /* ROOTPAD_USOCK_H */
