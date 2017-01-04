package br.wedo.checktool;

import java.util.*;

import static org.junit.Assert.*;
import org.junit.*;

public class AppTest {
	@Test
	public void testObjExist() {
		App app = new App();
		assertEquals(app.objExist(null, "", false), false);
	}
}