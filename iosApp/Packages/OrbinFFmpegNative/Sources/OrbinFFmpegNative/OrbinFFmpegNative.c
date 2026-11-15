#include "OrbinFFmpegNative.h"

// Declarations verified against ffmpegkit_wrapper.h in the checksum-pinned artifact.
// Including that framework's umbrella would also import unrelated C++ headers into Swift.
extern void *ffmpeg_kit_create_session_from_argv(int argc, const char **argv);
extern void *ffprobe_kit_create_session_from_argv(int argc, const char **argv);
extern void ffmpeg_kit_session_execute(void *session);
extern void ffprobe_kit_session_execute(void *session);
extern void ffmpeg_kit_session_cancel(void *session);
extern void ffprobe_kit_cancel_session(int64_t id);
extern void ffmpeg_kit_close_session(void *session);
extern void ffprobe_kit_close_session(void *session);
extern int64_t ffmpeg_kit_session_get_session_id(void *session);
extern int64_t ffmpeg_kit_session_get_return_code(void *session);
extern char *ffmpeg_kit_session_get_output(void *session);
extern void ffmpeg_kit_free(void *value);
extern bool ffmpeg_kit_packages_get_is_gpl(void);
extern bool ffmpeg_kit_packages_get_is_nonfree(void);

void *orbin_ffmpeg_create(int count, const char *const *arguments, bool probe) {
    return probe ? ffprobe_kit_create_session_from_argv(count, (const char **)arguments)
                 : ffmpeg_kit_create_session_from_argv(count, (const char **)arguments);
}
void orbin_ffmpeg_execute(void *session, bool probe) {
    if (probe) ffprobe_kit_session_execute(session); else ffmpeg_kit_session_execute(session);
}
void orbin_ffmpeg_cancel(void *session, bool probe) {
    if (probe) ffprobe_kit_cancel_session(ffmpeg_kit_session_get_session_id(session));
    else ffmpeg_kit_session_cancel(session);
}
void orbin_ffmpeg_close(void *session, bool probe) {
    if (probe) ffprobe_kit_close_session(session); else ffmpeg_kit_close_session(session);
}
int64_t orbin_ffmpeg_result(void *session) { return ffmpeg_kit_session_get_return_code(session); }
char *orbin_ffmpeg_output(void *session) { return ffmpeg_kit_session_get_output(session); }
void orbin_ffmpeg_free(void *value) { ffmpeg_kit_free(value); }
bool orbin_ffmpeg_is_gpl(void) { return ffmpeg_kit_packages_get_is_gpl(); }
bool orbin_ffmpeg_is_nonfree(void) { return ffmpeg_kit_packages_get_is_nonfree(); }
