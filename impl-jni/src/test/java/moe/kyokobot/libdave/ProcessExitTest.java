package moe.kyokobot.libdave;

public class ProcessExitTest extends ProcessExitTestBase {
    @Override
    Class<? extends DaveFactory> getFactoryClass() {
        return NativeDaveFactory.class;
    }
}
