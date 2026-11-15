#ifndef ORBIN_FFMPEG_NATIVE_H
#define ORBIN_FFMPEG_NATIVE_H
#include <stdbool.h>
#include <stdint.h>

// Initializes the native backend once across every calling thread.
void orbin_ffmpeg_initialize(void);

// Only the pinned library's C ABI crosses into Swift, never its C++ umbrella headers.
void *orbin_ffmpeg_create(int count, const char *const *arguments, bool probe);
void orbin_ffmpeg_execute(void *session, bool probe);
void orbin_ffmpeg_cancel(void *session, bool probe);
void orbin_ffmpeg_close(void *session, bool probe);
int64_t orbin_ffmpeg_result(void *session);
char *orbin_ffmpeg_output(void *session);
void orbin_ffmpeg_free(void *value);
bool orbin_ffmpeg_is_gpl(void);
bool orbin_ffmpeg_is_nonfree(void);
#endif
