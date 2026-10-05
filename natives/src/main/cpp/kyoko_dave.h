#ifndef KYOKO_DAVE_H
#define KYOKO_DAVE_H

/*
 * Plain C ABI over libdave, shaped for FFM (Panama) downcalls. The JNI
 * bindings are thin adapters over these functions.
 *
 * Conventions:
 * - Handles are the same pointers the JNI bindings hand out as jlong.
 * - Buffers returned through (out_data, out_size) are allocated by the
 *   library and must be released with kyoko_dave_free.
 * - Callbacks take a user_data pointer. If user_data_free is non-NULL, it is
 *   called exactly once, after the library drops its last reference to the
 *   callback (replacement, owner destruction, or completion of a one-shot).
 */

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#define KYOKO_DAVE_EXPORT __declspec(dllexport)
#else
#define KYOKO_DAVE_EXPORT __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct kyoko_dave_session kyoko_dave_session;
typedef struct kyoko_dave_key_ratchet kyoko_dave_key_ratchet;
typedef struct kyoko_dave_encryptor kyoko_dave_encryptor;
typedef struct kyoko_dave_decryptor kyoko_dave_decryptor;
typedef struct kyoko_dave_roster kyoko_dave_roster;

typedef void (*kyoko_dave_user_data_free)(void *user_data);

/* file may be NULL. */
typedef void (*kyoko_dave_log_sink_callback)(int32_t severity,
                                             const char *file, int32_t line,
                                             const char *message,
                                             void *user_data);
typedef void (*kyoko_dave_mls_failure_callback)(const char *source,
                                                const char *reason,
                                                void *user_data);
typedef void (*kyoko_dave_pairwise_fingerprint_callback)(
    const uint8_t *fingerprint, size_t fingerprint_size, void *user_data);
typedef void (*kyoko_dave_protocol_version_changed_callback)(void *user_data);

enum {
  KYOKO_DAVE_COMMIT_RESULT_SUCCESS = 0,
  KYOKO_DAVE_COMMIT_RESULT_FAILED = -1,
  KYOKO_DAVE_COMMIT_RESULT_IGNORED = -2,
};

KYOKO_DAVE_EXPORT void kyoko_dave_free(void *ptr);

KYOKO_DAVE_EXPORT uint16_t kyoko_dave_max_supported_protocol_version(void);

/* A NULL callback discards all log output. */
KYOKO_DAVE_EXPORT void
kyoko_dave_set_log_sink(kyoko_dave_log_sink_callback callback, void *user_data,
                        kyoko_dave_user_data_free user_data_free);

/* Session */

/* callback may be NULL. It may be invoked from any thread that drives the
 * session. */
KYOKO_DAVE_EXPORT kyoko_dave_session *
kyoko_dave_session_create(const char *auth_session_id,
                          kyoko_dave_mls_failure_callback callback,
                          void *user_data,
                          kyoko_dave_user_data_free user_data_free);
KYOKO_DAVE_EXPORT void kyoko_dave_session_destroy(kyoko_dave_session *session);
KYOKO_DAVE_EXPORT void kyoko_dave_session_init(kyoko_dave_session *session,
                                               uint16_t version,
                                               uint64_t group_id,
                                               const char *self_user_id);
KYOKO_DAVE_EXPORT void kyoko_dave_session_reset(kyoko_dave_session *session);
KYOKO_DAVE_EXPORT void
kyoko_dave_session_set_protocol_version(kyoko_dave_session *session,
                                        uint16_t version);
KYOKO_DAVE_EXPORT uint16_t
kyoko_dave_session_get_protocol_version(kyoko_dave_session *session);
KYOKO_DAVE_EXPORT void
kyoko_dave_session_get_last_epoch_authenticator(kyoko_dave_session *session,
                                                uint8_t **out_data,
                                                size_t *out_size);
KYOKO_DAVE_EXPORT void
kyoko_dave_session_set_external_sender(kyoko_dave_session *session,
                                       const uint8_t *external_sender,
                                       size_t external_sender_size);
/* Returns false if there is no commit/welcome to send. */
KYOKO_DAVE_EXPORT bool kyoko_dave_session_process_proposals(
    kyoko_dave_session *session, const uint8_t *proposals,
    size_t proposals_size, const char *const *recognized_user_ids,
    size_t recognized_user_ids_count, uint8_t **out_data, size_t *out_size);
/* Returns a KYOKO_DAVE_COMMIT_RESULT_* value. On success *out_roster receives
 * a roster owned by the caller. */
KYOKO_DAVE_EXPORT int32_t kyoko_dave_session_process_commit(
    kyoko_dave_session *session, const uint8_t *commit, size_t commit_size,
    kyoko_dave_roster **out_roster);
/* Returns NULL on failure. */
KYOKO_DAVE_EXPORT kyoko_dave_roster *kyoko_dave_session_process_welcome(
    kyoko_dave_session *session, const uint8_t *welcome, size_t welcome_size,
    const char *const *recognized_user_ids, size_t recognized_user_ids_count);
KYOKO_DAVE_EXPORT void
kyoko_dave_session_get_marshalled_key_package(kyoko_dave_session *session,
                                              uint8_t **out_data,
                                              size_t *out_size);
/* Returns NULL if there is no ratchet for the user. */
KYOKO_DAVE_EXPORT kyoko_dave_key_ratchet *
kyoko_dave_session_get_key_ratchet(kyoko_dave_session *session,
                                   const char *user_id);
/* The callback is invoked once, on a separate native thread. */
KYOKO_DAVE_EXPORT void kyoko_dave_session_get_pairwise_fingerprint(
    kyoko_dave_session *session, uint16_t version, const char *user_id,
    kyoko_dave_pairwise_fingerprint_callback callback, void *user_data,
    kyoko_dave_user_data_free user_data_free);

/* Roster (result of commit/welcome processing) */

KYOKO_DAVE_EXPORT size_t kyoko_dave_roster_size(const kyoko_dave_roster *roster);
KYOKO_DAVE_EXPORT uint64_t
kyoko_dave_roster_get_user_id(const kyoko_dave_roster *roster, size_t index);
KYOKO_DAVE_EXPORT size_t
kyoko_dave_roster_get_key_size(const kyoko_dave_roster *roster, size_t index);
/* Valid until the roster is destroyed. */
KYOKO_DAVE_EXPORT const uint8_t *
kyoko_dave_roster_get_key_data(const kyoko_dave_roster *roster, size_t index);
KYOKO_DAVE_EXPORT void kyoko_dave_roster_destroy(kyoko_dave_roster *roster);

/* Key ratchet */

KYOKO_DAVE_EXPORT void
kyoko_dave_key_ratchet_get_encryption_key(kyoko_dave_key_ratchet *key_ratchet,
                                          uint32_t key_generation,
                                          uint8_t **out_data, size_t *out_size);
KYOKO_DAVE_EXPORT void
kyoko_dave_key_ratchet_delete_key(kyoko_dave_key_ratchet *key_ratchet,
                                  uint32_t key_generation);
KYOKO_DAVE_EXPORT void
kyoko_dave_key_ratchet_destroy(kyoko_dave_key_ratchet *key_ratchet);

/* Encryptor */

KYOKO_DAVE_EXPORT kyoko_dave_encryptor *kyoko_dave_encryptor_create(void);
KYOKO_DAVE_EXPORT void
kyoko_dave_encryptor_destroy(kyoko_dave_encryptor *encryptor);
/* Takes ownership of key_ratchet. */
KYOKO_DAVE_EXPORT void
kyoko_dave_encryptor_set_key_ratchet(kyoko_dave_encryptor *encryptor,
                                     kyoko_dave_key_ratchet *key_ratchet);
KYOKO_DAVE_EXPORT void
kyoko_dave_encryptor_set_passthrough_mode(kyoko_dave_encryptor *encryptor,
                                          bool passthrough_mode);
KYOKO_DAVE_EXPORT void
kyoko_dave_encryptor_assign_ssrc_to_codec(kyoko_dave_encryptor *encryptor,
                                          uint32_t ssrc, int32_t codec);
KYOKO_DAVE_EXPORT uint16_t
kyoko_dave_encryptor_get_protocol_version(kyoko_dave_encryptor *encryptor);
KYOKO_DAVE_EXPORT size_t kyoko_dave_encryptor_get_max_ciphertext_byte_size(
    kyoko_dave_encryptor *encryptor, int32_t media_type, size_t frame_size);
/* Returns the number of bytes written, or a negated DAVEEncryptorResultCode. */
KYOKO_DAVE_EXPORT int32_t kyoko_dave_encryptor_encrypt(
    kyoko_dave_encryptor *encryptor, int32_t media_type, uint32_t ssrc,
    const uint8_t *frame, size_t frame_size, uint8_t *encrypted_frame,
    size_t encrypted_frame_capacity);
/* callback may be NULL to clear it. */
KYOKO_DAVE_EXPORT void kyoko_dave_encryptor_set_protocol_version_changed_callback(
    kyoko_dave_encryptor *encryptor,
    kyoko_dave_protocol_version_changed_callback callback, void *user_data,
    kyoko_dave_user_data_free user_data_free);

/* Decryptor */

KYOKO_DAVE_EXPORT kyoko_dave_decryptor *kyoko_dave_decryptor_create(void);
KYOKO_DAVE_EXPORT void
kyoko_dave_decryptor_destroy(kyoko_dave_decryptor *decryptor);
/* Takes ownership of key_ratchet. */
KYOKO_DAVE_EXPORT void
kyoko_dave_decryptor_transition_to_key_ratchet(kyoko_dave_decryptor *decryptor,
                                               kyoko_dave_key_ratchet *key_ratchet);
KYOKO_DAVE_EXPORT void kyoko_dave_decryptor_transition_to_passthrough_mode(
    kyoko_dave_decryptor *decryptor, bool passthrough_mode);
KYOKO_DAVE_EXPORT size_t kyoko_dave_decryptor_get_max_plaintext_byte_size(
    kyoko_dave_decryptor *decryptor, int32_t media_type,
    size_t encrypted_frame_size);
/* Returns the number of bytes written, or a negated DAVEDecryptorResultCode. */
KYOKO_DAVE_EXPORT int32_t kyoko_dave_decryptor_decrypt(
    kyoko_dave_decryptor *decryptor, int32_t media_type,
    const uint8_t *encrypted_frame, size_t encrypted_frame_size,
    uint8_t *frame, size_t frame_capacity);

#ifdef __cplusplus
}
#endif

#endif // KYOKO_DAVE_H
