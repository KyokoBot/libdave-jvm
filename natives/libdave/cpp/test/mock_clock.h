#pragma once

#include "utils/clock.h"

namespace discord {
namespace dave {
namespace test {

class MockClock : public IClock {
public:
    TimePoint Now() const override { return now_; }

    void SetNow(TimePoint now) { now_ = now; }
    void Advance(Duration duration) { now_ += duration; }

private:
    TimePoint now_{std::chrono::steady_clock::now()};
};

} // namespace test
} // namespace dave
} // namespace discord
