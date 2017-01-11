package br.wedo.checktool;

import java.io.IOException;
import java.io.InputStream;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
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
 * <h1>Checktool</h1> Programa responsável por validar a importacao dos objetos do arquivo ASM no banco de dados.
 * @author Dario Alves Junior
 * @version 1.0.0
 * @since 2016-12-19
 */
public class App {
  
  public static final String XML_MANIFEST_PATH = "export/manifest.xml";
  public static final String XML_MANIFEST_OBJECT_UUID = "objectUUID";
  public static final String XML_MANIFEST_OBJ_UUID = "objUUID";
  private static final String SQL_SEL_OBJ_INFO = "SELECT obj_uuid FROM af_t_obj WHERE obj_uuid=?";
  private static final String SQL_SEL_OBJSPEC_INFO = "SELECT obj_uuid FROM af_t_obj WHERE obj_spec_uuid=?";

  private static final Logger log = Logger.getLogger(App.class.getName());
  
  /**
   * Metodo de verificacao de objeto no banco de dados.
   * @param con Conexao com banco de dados
   * @param objUuid Id do objeto
   * @param spec Se verdadeiro pesquisa por qualquer versao, se não pesquisa por versão específica
   * @return Verdadeiro caso o objeto exista no banco de dados.
   * @throws SQLException
   */
  public static boolean existsObject(Connection conn, String objUuid, boolean spec) throws SQLException {
        PreparedStatement ps = null;
      try
      {
        ps = conn.prepareStatement(spec ? SQL_SEL_OBJSPEC_INFO : SQL_SEL_OBJ_INFO);
        ps.setString(1, objUuid);
        return ps.executeQuery().next();
      }
      finally
      {
        if (ps != null) {
          try
          {
            ps.close();
          }
          catch (SQLException ex) {
            log.log(Level.SEVERE, "Error", ex);
          }
        }
      }
  }

  /**
   * Metodo de conexão com banco de dados.
   * @param dbUrl Conexao com banco de dados
   * @param user usuario de acesso ao banco de dados
   * @param password Senha de acesso ao banco de dados
   * @return Conexão com banco de dados
   * @throws SQLException,
   *         ClassNotFoundException
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
  
  private static void printProgress(long startTime, long total, long current) throws IOException {
      long eta = current == 0 ? 0 : 
          (total - current) * (System.currentTimeMillis() - startTime) / current;

      String etaHms = current == 0 ? "N/A" : 
              String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(eta),
                      TimeUnit.MILLISECONDS.toMinutes(eta) % TimeUnit.HOURS.toMinutes(1),
                      TimeUnit.MILLISECONDS.toSeconds(eta) % TimeUnit.MINUTES.toSeconds(1));

      StringBuilder string = new StringBuilder(140);   
      int percent = (int) (current * 50 / total);
      string
          .append('\r')
          .append(String.join("", Collections.nCopies(percent == 0 ? 2 : 2 - (int) (Math.log10(percent)), " ")))
          .append(String.format(" %d%% [", percent*2))
          .append(String.join("", Collections.nCopies(percent, "=")))
          .append('>')
          .append(String.join("", Collections.nCopies(50 - percent, " ")))
          .append(']')
          .append(String.join("", Collections.nCopies((int)(Math.log10(total)) - (int)(Math.log10(current)), " ")))
          .append(String.format(" %d/%d, ETA: %s", current, total, etaHms));

      System.out.write("\r".getBytes());
      System.out.write(string.toString().getBytes());
  }

  /**
   * Metodo de entrada, valida os parametros e chama a rotina de validação.
   * @param con Conexao com banco de dados
   * @param file Arquivo AMS
   * @param spec Se verdadeiro pesquisa por qualquer versao, se não pesquisa por versão específica
   * @return Verdadeiro caso todos os objetos sejam importados
   * @throws SQLException,
   *         ParserConfigurationException
   */
  public static boolean checkUuids(Connection con, String file, boolean spec)
      throws SQLException, ParserConfigurationException {
    boolean retorno = true;
    try {
      
      ZipFile zfile = new ZipFile(file);
      ZipEntry entry = zfile.getEntry(XML_MANIFEST_PATH);
      if (entry != null) {
        InputStream stream = zfile.getInputStream(entry);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
        Document doc = null;
        try {
          doc = docBuilder.parse(stream);
          doc.getDocumentElement().normalize();
          NodeList nodeList = doc.getElementsByTagName("s");
          String objUuid = "";
          String objName = spec ? XML_MANIFEST_OBJECT_UUID : XML_MANIFEST_OBJ_UUID;
          int total = nodeList.getLength();
          long startTime = System.currentTimeMillis();
          for (int temp = 0; temp < total; temp++) {
            printProgress(startTime, total, temp+1);
            Node node = nodeList.item(temp);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
              Element element = (Element) node;
              String name = element.getAttribute("n");
              if (name.contains(objName)) {
                objUuid = element.getTextContent();
                //existsObject
                if (!existsObject(con, objUuid, spec)) {
                  System.out.println("\rObject: " + objUuid + " was not imported");
                  retorno = false;
                }
              }
            }
          }
          //System.out.write("\r".getBytes());
        } catch (SAXException saxEx) {
          log.log(Level.SEVERE, "Error", saxEx);
        } catch (IOException ioEx) {
          log.log(Level.SEVERE, "Error", ioEx);
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
      
      String inputFilePath = cmd.getOptionValue("ams");
      boolean spec = !cmd.hasOption("force");
      String databaseConnection = cmd.getOptionValue("connection");
      String userConnection = cmd.getOptionValue("user");
      String passwordConnection = cmd.getOptionValue("password");

      Connection con = getConnection(databaseConnection, userConnection, passwordConnection);
      System.out.println("\nChecktool - Starting checking installed objects...\n");
      boolean importSuccess = checkUuids(con, inputFilePath, spec);
      System.out.println(String.format("\nInstalation Status: %s\n",
          importSuccess ? "All objects have been successfully imported"
                    : "The installation was not completed successfully"));
      con.close();
    } catch (ParseException parEx) {
      System.out.println(parEx.getMessage());
      formatter.printHelp("checktool", options);
      Runtime.getRuntime().exit(1);
      return;
    } catch (Exception ex) {
      log.log(Level.SEVERE, "Error opening ams file", ex);
    }
  }
}
