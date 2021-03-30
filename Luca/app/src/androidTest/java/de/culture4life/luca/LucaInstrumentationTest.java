package de.culture4life.luca;

import androidx.test.platform.app.InstrumentationRegistry;

public class LucaInstrumentationTest {

    protected LucaApplication application;

    public LucaInstrumentationTest() {
        this.application = getInstrumentedApplication();
    }

    protected LucaApplication getInstrumentedApplication() {
        return (LucaApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
    }

}
