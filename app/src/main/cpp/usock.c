/*
 * usock.c - abstract Unix ドメインソケット
 *
 * abstract ネームスペースはファイルシステムの権限に依存せず、
 * SELinux が root プロセスを許可する範囲で利用できるため採用する。
 */
#include "usock.h"

#include <errno.h>
#include <stddef.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

int rp_usock_listen(const char *name)
{
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return -1;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    size_t namelen = strlen(name);
    if (namelen == 0 || namelen + 1 > sizeof(addr.sun_path)) {
        close(fd);
        return -1;
    }
    /* 先頭バイトを \0 にすることで abstract ネームスペースになる */
    memcpy(&addr.sun_path[1], name, namelen);

    socklen_t len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + namelen);
    if (bind(fd, (struct sockaddr *)&addr, len) < 0) {
        close(fd);
        return -1;
    }
    if (listen(fd, 1) < 0) {
        close(fd);
        return -1;
    }
    return fd;
}

int rp_usock_accept(int lfd)
{
    int fd = accept4(lfd, NULL, NULL, SOCK_CLOEXEC | SOCK_NONBLOCK);
    return fd;
}
