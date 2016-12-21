package main.java.br.wedo.checktool;

import java.io.InputStream;
import java.io.IOException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

public class App {

	private static final Logger log = Logger.getLogger(App.class.getName());

	public static boolean ObjetExist(Connection con, String objUUID) throws SQLException {
		String sql = "SELECT obj_uuid FROM af_t_obj o WHERE o.obj_spec_uuid = '" + objUUID + "'";
		Statement stmt = con.createStatement();
		ResultSet res = stmt.executeQuery(sql);

		String obj_uuid = "";

		try {
			while (res.next()) {
				obj_uuid = res.getString("obj_uuid");
			}
			res.close();
			stmt.close();
		} catch (Exception e) {
			 log.log(Level.SEVERE, "Error", e);
		}
		return !obj_uuid.isEmpty();
	}

	public static Connection getConnection(String dbURL, String user, String password)
			throws SQLException, ClassNotFoundException {
		Class.forName("oracle.jdbc.driver.OracleDriver");

		Properties props = new Properties();
		props.put("user", user);
		props.put("password", password);

		props.put("autoReconnect", "true");

		return DriverManager.getConnection(dbURL, props);
	}

	public static boolean checkUUIDs(Connection con, String file) throws SQLException, ParserConfigurationException {

		boolean retorno = true;
		try {

			ZipFile zfile = new ZipFile(file);
			String myFile = "export/manifest.xml";

			ZipEntry entry = zfile.getEntry(myFile);
			if (entry != null) {
				InputStream stream = zfile.getInputStream(entry);

				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = null;
				try {
					doc = dBuilder.parse(stream);
				} catch (SAXException e) {
					log.log(Level.SEVERE, "Error", e);
				} catch (IOException e) {
					log.log(Level.SEVERE, "Error", e);
				}

				doc.getDocumentElement().normalize();

				NodeList nList = doc.getElementsByTagName("s");

				String objUUID = "";

				for (int temp = 0; temp < nList.getLength(); temp++) {

					Node nNode = nList.item(temp);

					if (nNode.getNodeType() == Node.ELEMENT_NODE) {

						Element eElement = (Element) nNode;

						String name = eElement.getAttribute("n");

						if (name.contains("objectUUID")) {
							objUUID = eElement.getTextContent();
							boolean r = ObjetExist(con, objUUID);
							if (r == false) {
								System.out.println("\nobjUUID: " + objUUID + " not imported");
								retorno = false;
							}
						}

					}

				}
				stream.close();
			}
			zfile.close();

		} catch (IOException ioe) {
			log.log(Level.SEVERE, "Error opening zip file ", ioe);
		}

		return retorno;
	}

	public static void main(String[] args) throws Exception {

		Options options = new Options();

		Option optionFile = new Option("a", "ams", true, "input ams file path");
		optionFile.setRequired(true);
		options.addOption(optionFile);

		Option optionConnection = new Option("c", "connection", true,
				"database connection - jdbc:oracle:thin:@server:port/service_name");
		optionConnection.setRequired(true);
		options.addOption(optionConnection);

		Option optionUser = new Option("u", "user", true, "database user");
		optionUser.setRequired(true);
		options.addOption(optionUser);

		Option optionPassword = new Option("p", "password", true, "database password");
		optionPassword.setRequired(true);
		options.addOption(optionPassword);

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("checktool", options);
			System.exit(1);
			return;
		}

		String inputFilePath = cmd.getOptionValue("ams");
		String databaseConnection = cmd.getOptionValue("connection");
		String userConnection = cmd.getOptionValue("user");
		String passwordConnection = cmd.getOptionValue("password");

		try {
			Connection con = getConnection(databaseConnection, userConnection, passwordConnection);

			System.out.println("\nStarting checking installed objects...");
			log.info("Starting checking installed objects...");

			boolean ret = checkUUIDs(con, inputFilePath);

			System.out.println(ret ? "\nInstalation Status: All objects have been successfully imported"
					: "\nInstalation Status: The installation was not completed successfully");

			con.close();

		} catch (Exception e) {
			log.log(Level.SEVERE, "Error opening zip file ", e);
		}
	}

}
