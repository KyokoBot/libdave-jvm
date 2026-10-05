#ifndef KYOKO_DAVE_INTERNAL
#define KYOKO_DAVE_INTERNAL

#include "kyoko_dave.h"
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <vector>

namespace kyoko::libdave {

// Pairs a C callback with its user_data and releases the user_data once the
// last copy of the std::function capturing this holder is gone.
template <typename Callback> class CallbackHolder {
private:
  Callback callback_;
  void *userData_;
  kyoko_dave_user_data_free userDataFree_;

public:
  CallbackHolder(Callback callback, void *userData,
                 kyoko_dave_user_data_free userDataFree) noexcept
      : callback_(callback), userData_(userData), userDataFree_(userDataFree) {}

  ~CallbackHolder() {
    if (userDataFree_ != nullptr) {
      userDataFree_(userData_);
    }
  }

  CallbackHolder(const CallbackHolder &) = delete;
  CallbackHolder &operator=(const CallbackHolder &) = delete;

  explicit operator bool() const noexcept { return callback_ != nullptr; }

  template <typename... Args> void operator()(Args... args) const {
    if (callback_ != nullptr) {
      callback_(args..., userData_);
    }
  }
};

template <typename Callback>
std::shared_ptr<CallbackHolder<Callback>>
makeCallbackHolder(Callback callback, void *userData,
                   kyoko_dave_user_data_free userDataFree) {
  return std::make_shared<CallbackHolder<Callback>>(callback, userData,
                                                    userDataFree);
}

// Hands a copy of data to the caller, to be released with kyoko_dave_free.
static inline void copyToOutputBuffer(const std::vector<uint8_t> &data,
                                      uint8_t **outData, size_t *outSize) {
  if (outData == nullptr || outSize == nullptr) {
    return;
  }

  *outData = nullptr;
  *outSize = 0;
  if (data.empty()) {
    return;
  }

  auto buffer = static_cast<uint8_t *>(std::malloc(data.size()));
  if (buffer == nullptr) {
    return;
  }
  std::memcpy(buffer, data.data(), data.size());
  *outData = buffer;
  *outSize = data.size();
}

} // namespace kyoko::libdave

#endif // KYOKO_DAVE_INTERNAL
