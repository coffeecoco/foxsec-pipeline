package com.mozilla.secops.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class StateTest {
    public StateTest() {
    }

    @Test
    public void testStateConstruct() throws Exception {
        State s = new State(new SimpleStateInterface());
        assertNotNull(s);
        s.initialize();
    }

    @Test
    public void testSimpleStateSetGet() throws Exception {
        State s = new State(new SimpleStateInterface());
        assertNotNull(s);
        s.initialize();
        StateTestClass t = new StateTestClass();
        assertNotNull(t);
        t.str = "test";
        s.set("testing", t);
        t = s.get("testing", StateTestClass.class);
        assertNotNull(t);
        assertEquals("test", t.str);
    }

    @Test
    public void testSimpleStateSetGetNoExist() throws Exception {
        State s = new State(new SimpleStateInterface());
        assertNotNull(s);
        s.initialize();
        StateTestClass t = new StateTestClass();
        assertNotNull(t);
        t.str = "test";
        s.set("testing", t);
        t = s.get("testing", StateTestClass.class);
        assertNotNull(t);
        assertEquals("test", t.str);

        assertNull(s.get("nonexist", StateTestClass.class));
    }
}
