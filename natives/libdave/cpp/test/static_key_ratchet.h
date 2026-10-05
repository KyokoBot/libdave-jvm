#pragma once

#include <string>

#include <dave/dave_interfaces.h>

namespace discord {
namespace dave {
namespace test {

EncryptionKey MakeStaticSenderKey(const std::string& userID);
EncryptionKey MakeStaticSenderKey(uint64_t u64userID);

class StaticKeyRatchet : public IKeyRatchet {
public:
    StaticKeyRatchet(const std::string& userId) noexcept;
    ~StaticKeyRatchet() noexcept override = default;

    EncryptionKey GetKey(KeyGeneration generation) noexcept override;
    void DeleteKey(KeyGeneration generation) noexcept override;
    // Stable per-user domain identity, mirroring how MlsKeyRatchet identifies its key domain
    std::vector<uint8_t> GetDomainIdentity() const noexcept override;

private:
    uint64_t u64userID_;
    std::vector<uint8_t> domainIdentity_;
};

} // namespace test
} // namespace dave
} // namespace discord
