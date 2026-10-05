#include <gtest/gtest.h>

#include <bytes/bytes.h>

#include "mls_key_ratchet.h"

#include "dave_test.h"

using namespace testing;

namespace discord {
namespace dave {
namespace test {

static const auto TestCipherSuite =
  ::mlspp::CipherSuite{::mlspp::CipherSuite::ID::P256_AES128GCM_SHA256_P256};

static ::mlspp::bytes_ns::bytes MakeBaseSecret(uint8_t fill)
{
    return ::mlspp::bytes_ns::bytes(std::vector<uint8_t>(kAesGcm128KeyBytes, fill));
}

class MlsKeyRatchetTests : public DaveTests {};

TEST_F(MlsKeyRatchetTests, MlsKeyRatchetDomainIdentity)
{
    MlsKeyRatchet ratchetA(TestCipherSuite, MakeBaseSecret(0xA5));
    MlsKeyRatchet sameDomainAsA(TestCipherSuite, MakeBaseSecret(0xA5));
    MlsKeyRatchet ratchetB(TestCipherSuite, MakeBaseSecret(0x5A));

    // the base secret is derived per (epoch, user), so ratchets built from the
    // same base secret share a key domain
    EXPECT_FALSE(ratchetA.GetDomainIdentity().empty());
    EXPECT_EQ(ratchetA.GetDomainIdentity(), sameDomainAsA.GetDomainIdentity());
    EXPECT_NE(ratchetA.GetDomainIdentity(), ratchetB.GetDomainIdentity());
}

TEST_F(MlsKeyRatchetTests, MlsKeyRatchetDomainIdentityStableAcrossUse)
{
    MlsKeyRatchet ratchet(TestCipherSuite, MakeBaseSecret(0xA5));
    auto identityAtCreation = ratchet.GetDomainIdentity();

    ratchet.GetKey(0);
    ratchet.GetKey(3);
    ratchet.DeleteKey(0);

    EXPECT_EQ(ratchet.GetDomainIdentity(), identityAtCreation);
}

} // namespace test
} // namespace dave
} // namespace discord
