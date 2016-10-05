package com.linkedin.venice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.linkedin.venice.client.exceptions.VeniceClientException;
import com.linkedin.venice.client.schema.SchemaReader;
import com.linkedin.venice.client.store.AbstractAvroStoreClient;
import com.linkedin.venice.client.store.AvroGenericStoreClient;
import com.linkedin.venice.client.store.AvroGenericStoreClientImpl;
import com.linkedin.venice.client.store.AvroStoreClientFactory;
import com.linkedin.venice.controllerapi.ControllerClient;
import com.linkedin.venice.controllerapi.ControllerResponse;
import com.linkedin.venice.controllerapi.JobStatusQueryResponse;
import com.linkedin.venice.controllerapi.MultiNodeResponse;
import com.linkedin.venice.controllerapi.MultiReplicaResponse;
import com.linkedin.venice.controllerapi.MultiStoreResponse;
import com.linkedin.venice.controllerapi.MultiVersionResponse;
import com.linkedin.venice.controllerapi.NewStoreResponse;
import com.linkedin.venice.controllerapi.SchemaResponse;
import com.linkedin.venice.controllerapi.StoreResponse;
import com.linkedin.venice.controllerapi.VersionResponse;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.meta.Version;
import com.linkedin.venice.utils.Utils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import org.apache.avro.Schema;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class AdminTool {

  // TODO: static state means this can only be used by command line,
  // if we want to use this class programmatically it should get refactored.
  private static ObjectWriter jsonWriter = new ObjectMapper().writerWithDefaultPrettyPrinter();
  private static List<String> fieldsToDisplay = new ArrayList<>();
  private static final String STATUS = "status";
  private static final String ERROR = "error";
  private static final String SUCCESS = "success";

  public static void main(String args[])
      throws ParseException, IOException, InterruptedException, ExecutionException, VeniceClientException {

    /* Command Options are split up for help text formatting, see printUsageAndExit() */
    Options options = new Options();
    OptionGroup commandGroup = new OptionGroup();
    for (Command c : Command.values()){
      createCommandOpt(c, commandGroup);
    }

    for (Arg arg : Arg.values()){
      createOpt(arg, arg.isParameterized(), arg.getHelpText(), options);
    }

    Options parameterOptionsForHelp = new Options();
    for (Object obj : options.getOptions()){
      Option o = (Option) obj;
      parameterOptionsForHelp.addOption(o);
    }

    options.addOptionGroup(commandGroup);

    CommandLineParser parser = new BasicParser();
    CommandLine cmd = parser.parse(options, args);

    try {
      if (cmd.hasOption(Arg.HELP.first())) {
        printUsageAndExit(commandGroup, parameterOptionsForHelp);
      }


      cmd = parser.parse(options, args);

      ensureOnlyOneCommand(cmd);

      String routerHosts = getRequiredArgument(cmd, Arg.URL);
      String clusterName = getRequiredArgument(cmd, Arg.CLUSTER);

      if (cmd.hasOption(Arg.FLAT_JSON.toString())){
        jsonWriter = new ObjectMapper().writer();
      }
      if (cmd.hasOption(Arg.FILTER_JSON.toString())){
        fieldsToDisplay = Arrays.asList(
            cmd.getOptionValue(Arg.FILTER_JSON.first()).split(","));
      }

      if (cmd.hasOption(Command.LIST_STORES.toString())){
        MultiStoreResponse storeResponse = ControllerClient.queryStoreList(routerHosts, clusterName);
        printObject(storeResponse);
      } else if (cmd.hasOption(Command.DESCRIBE_STORE.toString())){
        String storeName = getRequiredArgument(cmd, Arg.STORE, Command.DESCRIBE_STORE);
        for (String store : storeName.split(",")) {
          printStoreDescription(routerHosts, clusterName, store);
        }
      } else if (cmd.hasOption(Command.DESCRIBE_STORES.toString())){
        MultiStoreResponse storeResponse = ControllerClient.queryStoreList(routerHosts, clusterName);
        for (String store : storeResponse.getStores()) {
          printStoreDescription(routerHosts, clusterName, store);
        }
      } else if (cmd.hasOption(Command.JOB_STATUS.toString())){
        String storeName = getRequiredArgument(cmd, Arg.STORE, Command.JOB_STATUS);
        String versionString = getRequiredArgument(cmd, Arg.VERSION, Command.JOB_STATUS);
        int version = Integer.parseInt(versionString);
        String topicName = new Version(storeName, version).kafkaTopicName();
        JobStatusQueryResponse jobStatus = ControllerClient.queryJobStatus(routerHosts, clusterName, topicName);
        printObject(jobStatus);
      } else if (cmd.hasOption(Command.NEW_STORE.toString())) {
        createNewStore(cmd, routerHosts, clusterName);
      } else if (cmd.hasOption(Command.PAUSE_STORE.toString())) {
        setStorePaused(cmd, routerHosts, clusterName, true);
      } else if (cmd.hasOption(Command.RESUME_STORE.toString())){
        setStorePaused(cmd, routerHosts, clusterName, false);
      } else if (cmd.hasOption(Command.SET_VERSION.toString())){
        applyVersionToStore(cmd, routerHosts, clusterName);
      } else if (cmd.hasOption(Command.ADD_SCHEMA.toString())){
        applyValueSchemaToStore(cmd, routerHosts, clusterName);
      } else if (cmd.hasOption(Command.LIST_STORAGE_NODES.toString())) {
        printStorageNodeList(routerHosts, clusterName);
      } else if (cmd.hasOption(Command.NODE_REMOVABLE.toString())){
        isNodeRemovable(cmd, routerHosts, clusterName);
      } else if (cmd.hasOption(Command.REPLICAS_OF_STORE.toString())) {
        printReplicaListForStoreVersion(cmd, routerHosts, clusterName);
      } else if (cmd.hasOption(Command.REPLICAS_ON_STORAGE_NODE.toString())) {
        printReplicaListForStorageNode(cmd, routerHosts, clusterName);
      } else if (cmd.hasOption(Command.QUERY.toString())){
        queryStoreForKey(cmd, routerHosts);
      } else {
        StringJoiner availableCommands = new StringJoiner(", ");
        for (Command c : Command.values()){
          availableCommands.add("--" + c.toString());
        }
        throw new VeniceException("Must supply one of the following commands: " + availableCommands.toString());
      }

    } catch (VeniceException e){
      printErrAndExit(e.getMessage());
    }
  }

  private static void ensureOnlyOneCommand(CommandLine cmd){
    String foundCommand = null;
    for (Command c : Command.values()){
      if (cmd.hasOption(c.toString())){
        if (null == foundCommand) {
          foundCommand = c.toString();
        } else {
          throw new VeniceException("Can only specify one of --" + foundCommand + " and --" + c.toString());
        }
      }
    }
  }

  private static void queryStoreForKey(CommandLine cmd, String routerHosts)
      throws VeniceClientException, ExecutionException, InterruptedException, JsonProcessingException {
    String store = getRequiredArgument(cmd, Arg.STORE);
    Schema keySchema = null;
    SchemaReader schemaReader = null;
    try {
      AvroGenericStoreClient<Object> schemaClient = AvroStoreClientFactory.getAvroGenericStoreClient(routerHosts, store);
      AbstractAvroStoreClient<Object> castClient = (AvroGenericStoreClientImpl<Object>) schemaClient;
      schemaReader = new SchemaReader(castClient);
      keySchema = schemaReader.getKeySchema();
    } catch (VeniceClientException e) {
      printErrAndExit(e.getMessage());
    } finally {
      if (null != schemaReader) {
        schemaReader.close(); /* closes internal client that was passed in */
      }
    }
    String keyString = getRequiredArgument(cmd, Arg.KEY);
    Object key = null;
    switch (keySchema.getType()){
      case DOUBLE:
        key = Double.parseDouble(keyString);
        break;
      case LONG:
        key = Long.parseLong(keyString);
        break;
      case STRING:
        key = keyString;
        break;
      /*
      case RECORD: // This probably wont work, we can revisit with future testing
        key = new GenericDatumReader<>(keySchema)
            .read(null, new JsonDecoder(keySchema, new ByteArrayInputStream(keyString.getBytes())));
        break;
      */
      default:
        throw new VeniceException("Cannot handle key type, found key schema: " + keySchema.toString());
    }

    Map<String, String> outputMap = new HashMap<>();
    outputMap.put("key-class", key.getClass().getCanonicalName());
    outputMap.put("key", keyString);

    Object value;
    try(AvroGenericStoreClient<Object> client = AvroStoreClientFactory.getAvroGenericStoreClient(routerHosts, store)) {
      value = client.get(key).get();
    }
    outputMap.put("value-class", value.getClass().getCanonicalName());
    outputMap.put("value", value.toString());
    printObject(outputMap);
  }

  private static void createNewStore(CommandLine cmd, String routerHosts, String clusterName)
      throws IOException {
    String store = getRequiredArgument(cmd, Arg.STORE, Command.NEW_STORE);
    String keySchemaFile = getRequiredArgument(cmd, Arg.KEY_SCHEMA, Command.NEW_STORE);
    String keySchema = readFile(keySchemaFile);
    String valueSchemaFile = getRequiredArgument(cmd, Arg.VALUE_SCHEMA, Command.NEW_STORE);
    String valueSchema = readFile(valueSchemaFile);
    String owner = getRequiredArgument(cmd, Arg.OWNER, Command.NEW_STORE);
    verifyValidSchema(keySchema);
    verifyValidSchema(valueSchema);
    verifyStoreExistence(routerHosts, clusterName, store, false);
    NewStoreResponse newStore = ControllerClient.createNewStore(routerHosts, clusterName, store, owner, keySchema,
        valueSchema);
    printObject(newStore);
  }

  private static void setStorePaused(CommandLine cmd, String routerHosts, String clusterName, boolean newPauseStatus){
    String store = getRequiredArgument(cmd, Arg.STORE, Command.SET_VERSION);
    ControllerResponse response = ControllerClient.setPauseStatus(routerHosts, clusterName, store, newPauseStatus);
    printSuccess(response);
  }

  private static void applyVersionToStore(CommandLine cmd, String routerHosts, String clusterName){
    String store = getRequiredArgument(cmd, Arg.STORE, Command.SET_VERSION);
    String version = getRequiredArgument(cmd, Arg.VERSION, Command.SET_VERSION);
    int intVersion = Utils.parseIntFromString(version, Arg.VERSION.name());
    boolean versionExists = false;
    MultiVersionResponse allVersions = ControllerClient.queryActiveVersions(routerHosts, clusterName, store);
    if (allVersions.isError()){
      throw new VeniceException("Error querying versions for store: " + store + " -- " + allVersions.getError());
    }
    for (int v : allVersions.getVersions()){
      if (v == intVersion){
        versionExists = true;
        break;
      }
    }
    if (!versionExists){
      throw new VeniceException("Version " + version + " does not exist for store " + store + ".  Store only has versions: " + Arrays.toString(allVersions.getVersions()));
    }
    VersionResponse response = ControllerClient.overrideSetActiveVersion(routerHosts, clusterName, store, intVersion);
    printSuccess(response);
  }

  private static void applyValueSchemaToStore(CommandLine cmd, String routerHosts, String clusterName)
      throws IOException {
    String store = getRequiredArgument(cmd, Arg.STORE, Command.ADD_SCHEMA);
    String valueSchemaFile = getRequiredArgument(cmd, Arg.VALUE_SCHEMA, Command.ADD_SCHEMA);
    String valueSchema = readFile(valueSchemaFile);
    verifyValidSchema(valueSchema);
    SchemaResponse valueResponse = ControllerClient.addValueSchema(routerHosts, clusterName, store, valueSchema);
    if (valueResponse.isError()) {
      throw new VeniceException("Error updating store with schema: " + valueResponse.getError());
    }
    printObject(valueResponse);
  }

  private static void printStoreDescription(String routerHosts, String clusterName, String storeName)
      throws JsonProcessingException {
    StoreResponse response = ControllerClient.getStore(routerHosts, clusterName, storeName);
    printObject(response);
  }

  private static void printStorageNodeList(String routerHosts, String clusterName){
    MultiNodeResponse nodeResponse = ControllerClient.listStorageNodes(routerHosts, clusterName);
    printObject(nodeResponse);
  }

  private static void printReplicaListForStoreVersion(CommandLine cmd, String routerHosts, String clusterName){
    String store = getRequiredArgument(cmd, Arg.STORE, Command.REPLICAS_OF_STORE);
    int version = Utils.parseIntFromString(getRequiredArgument(cmd, Arg.VERSION, Command.REPLICAS_OF_STORE),
        Arg.VERSION.toString());
    MultiReplicaResponse response = ControllerClient.listReplicas(routerHosts, clusterName, store, version);
    printObject(response);
  }

  private static void printReplicaListForStorageNode(CommandLine cmd, String routerHosts, String clusterName){
    String storageNodeId = getRequiredArgument(cmd, Arg.STORAGE_NODE);
    MultiReplicaResponse response = ControllerClient.listStorageNodeReplicas(routerHosts, clusterName, storageNodeId);
    printObject(response);
  }

  private static void isNodeRemovable(CommandLine cmd, String routerHosts, String clusterName){
    String storageNodeId = getRequiredArgument(cmd, Arg.STORAGE_NODE);
    ControllerResponse response = ControllerClient.isNodeRemovable(routerHosts, clusterName, storageNodeId);
    printSuccess(response);
  }


  /* Things that are not commands */

  private static void printUsageAndExit(OptionGroup commandGroup, Options options){

    /* Commands */
    String command = "java -jar " + new java.io.File(AdminTool.class.getProtectionDomain()
        .getCodeSource()
        .getLocation()
        .getPath())
        .getName();
    new HelpFormatter().printHelp(command + " --<command> [parameters]\n\nCommands:",
        new Options().addOptionGroup(commandGroup));

    /* Parameters */
    new HelpFormatter().printHelp("Parameters: ", options);

    /* Examples */
    System.err.println("\nExamples:");
    for (Command c : Command.values()){
      StringJoiner exampleArgs = new StringJoiner(" ");
      for (Arg a : c.getRequiredArgs()){
        exampleArgs.add("--" + a.toString());
        exampleArgs.add("<" + a.toString() + ">");
      }
      System.err.println(command + " --" + c.toString() + " " + exampleArgs.toString());
    }
    System.exit(1);
  }

  private static String getRequiredArgument(CommandLine cmd, Arg arg){
    return getRequiredArgument(cmd, arg, "");
  }
  private static String getRequiredArgument(CommandLine cmd, Arg arg, Command command){
    return getRequiredArgument(cmd, arg, "when using --" + command.toString());
  }

  private static String getRequiredArgument(CommandLine cmd, Arg arg, String errorClause){
    if (!cmd.hasOption(arg.first())){
      printErrAndExit(arg.toString() + " is a required argument " + errorClause);
    }
    return cmd.getOptionValue(arg.first());
  }

  private static void verifyStoreExistence(String routerHosts, String cluster, String storename, boolean desiredExistence){
    MultiStoreResponse storeResponse = ControllerClient.queryStoreList(routerHosts, cluster);
    if (storeResponse.isError()){
      throw new VeniceException("Error verifying store exists: " + storeResponse.getError());
    }
    boolean storeExists = false;
    for (String s : storeResponse.getStores()){
      if (s.equals(storename)){
        storeExists = true;
        break;
      }
    }
    if (storeExists != desiredExistence) {
      throw new VeniceException("Store " + storename +
          (storeExists ? " already exists" : " does not exist"));
    }
  }

  private static void verifyValidSchema(String schema) {
    try {
      Schema.parse(schema);
    } catch (Exception e){
      Map<String, String> errMap = new HashMap<>();
      errMap.put("schema", schema);
      printErrAndExit("Invalid Schema: " + e.getMessage(), errMap);
    }
  }

  private static void printErrAndExit(String err) {
    Map<String, String> errMap = new HashMap<>();
    printErrAndExit(err, errMap);
  }

  private static void createOpt(Arg name, boolean hasArg, String help, Options options){
    options.addOption(new Option(name.first(), name.toString(), hasArg, help));
  }

  private static void createCommandOpt(Command command, OptionGroup group){
    group.addOption(
        OptionBuilder
            .withLongOpt(command.toString())
            .withDescription(command.getDesc())
            .create()
    );
  }

  static String readFile(String path) throws IOException {
    String fullPath = path.replace("~", System.getProperty("user.home"));
    byte[] encoded = Files.readAllBytes(Paths.get(fullPath));
    return new String(encoded, StandardCharsets.UTF_8).trim();
  }

  ///// Print Output ////

  private static void printObject(Object response){
    try {
      if (fieldsToDisplay.size() == 0){
        System.out.println(jsonWriter.writeValueAsString(response));
      } else { // Only display specified keys
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter plainJsonWriter = mapper.writer();
        String jsonString = plainJsonWriter.writeValueAsString(response);
        Map<String, Object> printMap = mapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
        Map<String, Object> filteredPrintMap = new HashMap<>();
        printMap.entrySet().stream().filter(entry -> fieldsToDisplay.contains(entry.getKey())).forEach(entry -> {
          filteredPrintMap.put(entry.getKey(), entry.getValue());
            });
        System.out.println(jsonWriter.writeValueAsString(filteredPrintMap));
      }

    } catch (JsonProcessingException e) {
      System.out.println("{\"" + ERROR + "\":\"" + e.getMessage() + "\"}");
      System.exit(1);
    } catch (IOException e) {
      throw new VeniceException(e);
    }
  }

  static void printSuccess(ControllerResponse response){
    if (response.isError()){
      printErrAndExit(response.getError());
    } else {
      System.out.println("{\"" + STATUS + "\":\"" + SUCCESS + "\"}");
    }
  }

  private static void printErrAndExit(String errorMessage, Map<String, String> customMessages) {
    Map<String, String> errMap = new HashMap<>();
    for (Map.Entry<String, String> messagePair : customMessages.entrySet()){
      errMap.put(messagePair.getKey(), messagePair.getValue());
    }
    if (errMap.keySet().contains(ERROR)){
      errMap.put(ERROR, errMap.get(ERROR) + " " + errorMessage);
    } else {
      errMap.put(ERROR, errorMessage);
    }
    try {
      System.out.println(jsonWriter.writeValueAsString(errMap));
    } catch (JsonProcessingException e) {
      System.out.println("{\"" + ERROR + "\":\"" + e.getMessage() + "\"}");
    }
    System.exit(1);
  }

}
