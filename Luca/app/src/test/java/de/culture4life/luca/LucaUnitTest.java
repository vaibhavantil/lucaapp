package de.culture4life.luca;

import net.lachlanmckee.timberjunit.TimberTestRule;

import org.junit.Rule;

import androidx.test.core.app.ApplicationProvider;

public class LucaUnitTest {

    @Rule
    public TimberTestRule timberTestRule = TimberTestRule.logAllAlways();

    protected LucaApplication application;

    public LucaUnitTest() {
        this.application = ApplicationProvider.getApplicationContext();
    }

}
