package test.java.br.wedo.checktool;

import java.sql.SQLException;
import java.util.*;

import main.java.br.wedo.checktool.*;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppTest {
	@Test
	public void testObjExist() {
		App app = new App();
		try {
			assertEquals(app.objExist(null, "", false), false);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}