#include <gtest/gtest.h>

#include <mls/crypto.h>

#include <dave/key_serialization.h>
#include "mls/parameters.h"

#include "dave_test.h"

using namespace testing;

namespace discord {
namespace dave {
namespace test {

namespace {

constexpr ProtocolVersion kTestProtocolVersion = 1;

} // namespace

class KeySerializationTests : public DaveTests {};

TEST_F(KeySerializationTests, SigningKeyJwkRoundTrip)
{
    auto suite = mls::CiphersuiteForProtocolVersion(kTestProtocolVersion);
    auto key = ::mlspp::SignaturePrivateKey::generate(suite);

    auto jwk = mls::SigningKeyToJwk(key, kTestProtocolVersion);
    ASSERT_FALSE(jwk.empty());

    auto restored = mls::SigningKeyFromJwk(jwk, kTestProtocolVersion);
    ASSERT_NE(restored, nullptr);
    EXPECT_EQ(restored->public_key.data, key.public_key.data);
    EXPECT_EQ(mls::SigningKeyToJwk(*restored, kTestProtocolVersion), jwk);
}

TEST_F(KeySerializationTests, SigningKeyFromInvalidJwkReturnsNull)
{
    EXPECT_EQ(mls::SigningKeyFromJwk("not a jwk", kTestProtocolVersion), nullptr);
    EXPECT_EQ(mls::SigningKeyFromJwk("", kTestProtocolVersion), nullptr);
    EXPECT_EQ(mls::SigningKeyFromJwk("{}", kTestProtocolVersion), nullptr);
}

} // namespace test
} // namespace dave
} // namespace discord
