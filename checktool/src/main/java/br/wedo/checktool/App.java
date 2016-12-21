package main.java.br.wedo.checktool;

import java.io.IOException;
import java.io.InputStream;

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

/**
 * Classe responsável por validar a importacao dos objetos asm no banco de dados
 *
 * @author Dario Alves Junior
 */
public class App {

  private static final Logger log = Logger.getLogger(App.class.getName());

  /**
   * Metodo de verificacao de objeto no banco de dados.
   * @param con Conexao com banco de dados
   * @param objUuid Id do objeto
   * @param spec Se verdadeiro pesquisa por qualquer versao, se não pesquisa por versão específica
   * @return Verdadeiro caso o objeto exista no banco de dados
   * @throws SQLException
   */
  public static boolean objExist(Connection con, String objUuid, boolean spec) throws SQLException {
    String sql = String.format("SELECT obj_uuid FROM af_t_obj WHERE %1='%2' ", 
                                                spec ? "obj_spec_uuid" : "obj_uuid", objUuid);
    Statement stmt = con.createStatement();
    ResultSet res = stmt.executeQuery(sql);
    String objTemp = "";
    try {
      while (res.next()) {
        objTemp = res.getString("obj_uuid");
        break;
      }
      res.close();
      stmt.close();
    } catch (Exception ex) {
      log.log(Level.SEVERE, "Error", ex);
    }
    return !objTemp.isEmpty();
  }

  /**
   * Metodo de conexão com banco de dados.
   * @param dbUrl Conexao com banco de dados
   * @param user usuario de acesso ao banco de dados
   * @param password Senha de acesso ao banco de dados
   * @return Conexão com banco de dados
   * @throws SQLException, ClassNotFoundException
   */
  public static Connection getConnection(String dbUrl, String user, String password)
                throws SQLException, ClassNotFoundException {
    Class.forName("oracle.jdbc.driver.OracleDriver");

    Properties props = new Properties();
    props.put("user", user);
    props.put("password", password);
    props.put("autoReconnect", "true");

    return DriverManager.getConnection(dbUrl, props);
  }

  /**
   * Metodo de entrada, valida os parametros e chama a rotina de validação.
   * @param con Conexao com banco de dados
   * @param file Arquivo AMS
   * @param spec Se verdadeiro pesquisa por qualquer versao, se não pesquisa por versão específica
   * @return Verdadeiro caso todos os objetos sejam importados
   * @throws SQLException, ParserConfigurationException
   */
  public static boolean checkUuids(Connection con, String file, boolean spec) throws SQLException, 
                                                                ParserConfigurationException {

    boolean retorno = true;
    try {
      ZipFile zfile = new ZipFile(file);
      ZipEntry entry = zfile.getEntry("export/manifest.xml");
      if (entry != null) {
        InputStream stream = zfile.getInputStream(entry);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
        Document doc = null;
        try {
          doc = docBuilder.parse(stream);
        } catch (SAXException saxEx) {
          log.log(Level.SEVERE, "Error", saxEx);
        } catch (IOException ioEx) {
          log.log(Level.SEVERE, "Error", ioEx);
        }

        doc.getDocumentElement().normalize();
        NodeList nodeList = doc.getElementsByTagName("s");
        String objUuid = "";
        String objName = spec ? "objUUID" : "objectUUID";

        for (int temp = 0; temp < nodeList.getLength(); temp++) {
          Node node = nodeList.item(temp);
          if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String name = element.getAttribute("n");
            
            if (name.contains(objName)) {
              objUuid = element.getTextContent();
              if (!objExist(con, objUuid,spec)) {
                System.out.println("\nObject: " + objUuid + " not imported");
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

  /**
   * Metodo de entrada, valida os parametros e chama a rotina de validação.
   * @param args Argumentos da linha de comando
   */
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
    
    Option optionForce = new Option("f", "force", false, "force version by objUUID (Optional)");
    optionForce.setRequired(false);
    options.addOption(optionForce);

    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd;

    try {
      cmd = parser.parse(options, args);
    } catch (ParseException parEx) {
      System.out.println(parEx.getMessage());
      formatter.printHelp("checktool", options);
      System.exit(1);
      return;
    }

    String inputFilePath = cmd.getOptionValue("ams");
    boolean forceVersion = cmd.hasOption("force");
    String databaseConnection = cmd.getOptionValue("connection");
    String userConnection = cmd.getOptionValue("user");
    String passwordConnection = cmd.getOptionValue("password");

    try {
      Connection con = getConnection(databaseConnection, userConnection, passwordConnection);

      System.out.println("\nStarting checking installed objects...");

      boolean importSuccess = checkUuids(con, inputFilePath, forceVersion);
      
      System.out.println(String.format("\nInstalation Status: %s", importSuccess ? "All objects have been successfully imported"
				: "The installation was not completed successfully"));

      con.close();

    } catch (Exception ex) {
      log.log(Level.SEVERE, "Error opening ams file", ex);
    }
  }
}
