#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <bytes/bytes.h>
#include <limits>

#include "common.h"
#include "cryptor_manager.h"
#include "utils/clock.h"

#include "dave_test.h"
#include "mock_clock.h"
#include "static_key_ratchet.h"

using namespace testing;
using namespace std::chrono_literals;

namespace discord {
namespace dave {
namespace test {

// Gap can't be larger than the amount of bits allocated for it if we want to handle wraparound
// correctly
static_assert(kMaxGenerationGap < kGenerationWrap, "Gap can't be larger than wraparound value");

class MockKeyRatchet : public IKeyRatchet {
public:
    MockKeyRatchet()
    {
        ON_CALL(*this, GetKey).WillByDefault([](KeyGeneration generation) {
            auto userId = std::string("12345678901234567890");
            return MakeStaticSenderKey(userId + std::to_string(generation));
        });
    }
    MOCK_METHOD(EncryptionKey, GetKey, (KeyGeneration generation), (override, noexcept));
    MOCK_METHOD(void, DeleteKey, (KeyGeneration generation), (override, noexcept));

    std::vector<uint8_t> GetDomainIdentity() const noexcept override
    {
        return {'m', 'o', 'c', 'k'};
    }
};

class CryptorManagerTests : public DaveTests {};

TEST_F(CryptorManagerTests, CryptorManagerCheckMaxGap)
{
    auto mockKeyRatchet = std::make_unique<MockKeyRatchet>();
    EXPECT_CALL(*mockKeyRatchet, GetKey(0));
    EXPECT_CALL(*mockKeyRatchet, GetKey(kMaxGenerationGap));
    EXPECT_CALL(*mockKeyRatchet, GetKey(kMaxGenerationGap + 1));

    MockClock clock;
    CryptorManager cryptorManager{clock, std::move(mockKeyRatchet)};
    // Give plenty of room to not trigger the max lifetime generations check
    clock.Advance(kMaxGenerationGap * 48h);

    auto cryptor = cryptorManager.GetCryptor(0);
    EXPECT_NE(cryptor, nullptr);
    EXPECT_EQ(cryptorManager.GetCryptor(0), cryptor);
    EXPECT_NE(cryptorManager.GetCryptor(kMaxGenerationGap), nullptr);
    EXPECT_EQ(cryptorManager.GetCryptor(kMaxGenerationGap + 1), nullptr);
    cryptorManager.ReportCryptorSuccess(
      kMaxGenerationGap,
      static_cast<TruncatedSyncNonce>(kMaxGenerationGap << kRatchetGenerationShiftBits));
    EXPECT_NE(cryptorManager.GetCryptor(kMaxGenerationGap + 1), nullptr);
}

TEST_F(CryptorManagerTests, CryptorManagerCheckExpiry)
{
    auto mockKeyRatchet = std::make_unique<MockKeyRatchet>();
    EXPECT_CALL(*mockKeyRatchet, GetKey(0));
    EXPECT_CALL(*mockKeyRatchet, GetKey(1));
    EXPECT_CALL(*mockKeyRatchet, DeleteKey(0));

    MockClock clock;
    CryptorManager cryptorManager{clock, std::move(mockKeyRatchet)};
    EXPECT_NE(cryptorManager.GetCryptor(0), nullptr);
    clock.Advance(1000000h);
    EXPECT_NE(cryptorManager.GetCryptor(0), nullptr);
    EXPECT_NE(cryptorManager.GetCryptor(1), nullptr);
    cryptorManager.ReportCryptorSuccess(1, 1 << kRatchetGenerationShiftBits);
    clock.Advance(kCryptorExpiry - 1us);
    EXPECT_NE(cryptorManager.GetCryptor(0), nullptr);
    clock.Advance(2us);
    EXPECT_EQ(cryptorManager.GetCryptor(0), nullptr);
}

TEST_F(CryptorManagerTests, CryptorManagerUpdateExpiryOnlyShortens)
{
    MockClock clock;
    CryptorManager cryptorManager{clock, std::make_unique<MockKeyRatchet>()};

    // A cryptor manager that has not been transitioned away from never expires
    clock.Advance(1000000h);
    EXPECT_FALSE(cryptorManager.IsExpired());

    // The first transition away from this ratchet schedules its expiry
    cryptorManager.UpdateExpiry(clock.Now() + kDefaultTransitionDuration);
    clock.Advance(kDefaultTransitionDuration / 2);
    EXPECT_FALSE(cryptorManager.IsExpired());

    // A later transition must not push the already scheduled expiry further out
    cryptorManager.UpdateExpiry(clock.Now() + kDefaultTransitionDuration);
    clock.Advance(kDefaultTransitionDuration / 2 + 1us);
    EXPECT_TRUE(cryptorManager.IsExpired());

    // A later transition with a shorter window still shortens the expiry
    CryptorManager shortLived{clock, std::make_unique<MockKeyRatchet>()};
    shortLived.UpdateExpiry(clock.Now() + kDefaultTransitionDuration);
    shortLived.UpdateExpiry(clock.Now() + 1s);
    clock.Advance(1s + 1us);
    EXPECT_TRUE(shortLived.IsExpired());
}

TEST_F(CryptorManagerTests, CryptorManagerDeleteOldKeys)
{
    auto mockKeyRatchet = std::make_unique<MockKeyRatchet>();
    EXPECT_CALL(*mockKeyRatchet, GetKey(0));
    EXPECT_CALL(*mockKeyRatchet, GetKey(5));
    EXPECT_CALL(*mockKeyRatchet, DeleteKey(0));
    EXPECT_CALL(*mockKeyRatchet, DeleteKey(1));
    EXPECT_CALL(*mockKeyRatchet, DeleteKey(2));
    EXPECT_CALL(*mockKeyRatchet, DeleteKey(3));
    EXPECT_CALL(*mockKeyRatchet, DeleteKey(4));

    MockClock clock;
    CryptorManager cryptorManager{clock, std::move(mockKeyRatchet)};
    // Give plenty of room to not trigger the max lifetime generations check
    clock.Advance(kMaxGenerationGap * 48h);

    EXPECT_NE(cryptorManager.GetCryptor(0), nullptr);
    EXPECT_NE(cryptorManager.GetCryptor(5), nullptr);
    cryptorManager.ReportCryptorSuccess(5, 5 << kRatchetGenerationShiftBits);
    clock.Advance(kCryptorExpiry + 1us);
    EXPECT_NE(cryptorManager.GetCryptor(5), nullptr);
}

TEST_F(CryptorManagerTests, CryptorManagerGenerationWrap)
{
    EXPECT_EQ(ComputeWrappedGeneration(0, 0), KeyGeneration{0});
    EXPECT_EQ(ComputeWrappedGeneration(0, 1), KeyGeneration{1});
    EXPECT_EQ(ComputeWrappedGeneration(0, 250), KeyGeneration{250});

    EXPECT_EQ(ComputeWrappedGeneration(11 * kGenerationWrap + 42, 42),
              KeyGeneration{11 * kGenerationWrap + 42});
    EXPECT_EQ(ComputeWrappedGeneration(11 * kGenerationWrap + 42, 50),
              KeyGeneration{11 * kGenerationWrap + 50});
    EXPECT_EQ(ComputeWrappedGeneration(11 * kGenerationWrap + 42, 10),
              KeyGeneration{12 * kGenerationWrap + 10});
}

TEST_F(CryptorManagerTests, CryptorManagerBigNonce)
{
    EXPECT_EQ(ComputeWrappedBigNonce(0, 0), 0u);
    EXPECT_EQ(ComputeWrappedBigNonce(0, 1), 1u);
    EXPECT_EQ(ComputeWrappedBigNonce(0, 250), 250u);

    EXPECT_EQ(ComputeWrappedBigNonce(11, 10), 11 << kRatchetGenerationShiftBits | 10u);
    EXPECT_EQ(ComputeWrappedBigNonce(11, 42), 11 << kRatchetGenerationShiftBits | 42u);
    EXPECT_EQ(ComputeWrappedBigNonce(11, 50), 11 << kRatchetGenerationShiftBits | 50u);

    EXPECT_EQ(ComputeWrappedBigNonce(11, 2 << kRatchetGenerationShiftBits | 34),
              11 << kRatchetGenerationShiftBits | 34u);
    EXPECT_EQ(ComputeWrappedBigNonce(11, 37 << kRatchetGenerationShiftBits | 139),
              11 << kRatchetGenerationShiftBits | 139u);
    EXPECT_EQ(ComputeWrappedBigNonce(11, 89 << kRatchetGenerationShiftBits | 294),
              11 << kRatchetGenerationShiftBits | 294u);
}

TEST_F(CryptorManagerTests, CryptorManagerCapturesDomainIdentity)
{
    MockClock clock;

    auto userId = std::string("12345678901234567890");
    CryptorManager cryptorManager{clock, std::make_unique<StaticKeyRatchet>(userId)};
    EXPECT_EQ(cryptorManager.GetDomainIdentity(),
              std::vector<uint8_t>(userId.begin(), userId.end()));
}

TEST_F(CryptorManagerTests, CryptorManagerNoReprocess)
{
    auto mockKeyRatchet = std::make_unique<MockKeyRatchet>();
    EXPECT_CALL(*mockKeyRatchet, GetKey(0));

    MockClock clock;
    CryptorManager cryptorManager{clock, std::move(mockKeyRatchet)};
    // Give plenty of room to not trigger the max lifetime generations check
    clock.Advance(kMaxGenerationGap * 48h);

    auto cryptor = cryptorManager.GetCryptor(0);
    EXPECT_NE(cryptor, nullptr);

    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 0));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 1));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 2));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 3));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, std::numeric_limits<TruncatedSyncNonce>::max()));
    cryptorManager.ReportCryptorSuccess(0, 0);
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 0));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 1));
    cryptorManager.ReportCryptorSuccess(0, 1);
    cryptorManager.ReportCryptorSuccess(0, 2);
    cryptorManager.ReportCryptorSuccess(0, 5);
    cryptorManager.ReportCryptorSuccess(0, 7);
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 0));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 1));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 2));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 5));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 7));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 3));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 4));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 6));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 8));
    cryptorManager.ReportCryptorSuccess(0, 4);
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 3));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 4));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 6));
    cryptorManager.ReportCryptorSuccess(0, 6);
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 3));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 6));
    cryptorManager.ReportCryptorSuccess(0, 10 + kMaxMissingNonces);
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 3));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 7));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 8));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 9));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 10));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 11));
}

TEST_F(CryptorManagerTests, CryptorManagerEarlyMissingNonces)
{
    auto mockKeyRatchet = std::make_unique<MockKeyRatchet>();

    MockClock clock;
    CryptorManager cryptorManager{clock, std::move(mockKeyRatchet)};

    // The first nonce we see is not the first nonce that was generated: the ones before it are
    // missing, not non-existent, so they must still be processable if they arrive late.
    cryptorManager.ReportCryptorSuccess(0, 3);
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 0));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 1));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 2));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 3));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 4));

    // Late arrivals are consumed one by one, leaving the others alone.
    cryptorManager.ReportCryptorSuccess(0, 1);
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 0));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 1));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 2));

    cryptorManager.ReportCryptorSuccess(0, 0);
    cryptorManager.ReportCryptorSuccess(0, 2);
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 0));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 2));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 4));
}

TEST_F(CryptorManagerTests, CryptorManagerEarlyMissingNoncesStartAtZero)
{
    auto mockKeyRatchet = std::make_unique<MockKeyRatchet>();

    MockClock clock;
    CryptorManager cryptorManager{clock, std::move(mockKeyRatchet)};

    // Starting at nonce 0 leaves nothing missing behind it.
    cryptorManager.ReportCryptorSuccess(0, 0);
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 0));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 1));
}

TEST_F(CryptorManagerTests, CryptorManagerEarlyMissingNoncesCapped)
{
    auto mockKeyRatchet = std::make_unique<MockKeyRatchet>();

    MockClock clock;
    CryptorManager cryptorManager{clock, std::move(mockKeyRatchet)};

    // A very late stream start only tracks the most recent kMaxMissingNonces nonces.
    constexpr TruncatedSyncNonce kFirstNonce = kMaxMissingNonces + 5;
    cryptorManager.ReportCryptorSuccess(0, kFirstNonce);
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 0));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, 4));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, 5));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, kFirstNonce - 1));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(0, kFirstNonce));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, kFirstNonce + 1));
}

TEST_F(CryptorManagerTests, CryptorManagerEarlyMissingNoncesAcrossGenerations)
{
    auto mockKeyRatchet = std::make_unique<MockKeyRatchet>();

    MockClock clock;
    CryptorManager cryptorManager{clock, std::move(mockKeyRatchet)};

    // The bootstrap gap is measured on the wrapped big nonce, so an early generation-0 packet
    // arriving after a generation-1 one is still processable.
    constexpr TruncatedSyncNonce kFirstNonce = 1 << kRatchetGenerationShiftBits | 2;
    cryptorManager.ReportCryptorSuccess(1, kFirstNonce);
    EXPECT_TRUE(cryptorManager.CanProcessNonce(1, kFirstNonce - 1));
    EXPECT_FALSE(cryptorManager.CanProcessNonce(1, kFirstNonce));
    EXPECT_TRUE(cryptorManager.CanProcessNonce(0, (1 << kRatchetGenerationShiftBits) - 1));
}

} // namespace test
} // namespace dave
} // namespace discord
