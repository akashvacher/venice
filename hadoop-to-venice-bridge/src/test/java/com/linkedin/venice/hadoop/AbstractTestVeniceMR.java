package com.linkedin.venice.hadoop;

import com.linkedin.venice.compression.CompressionStrategy;
import com.linkedin.venice.hadoop.ssl.TempFileSSLConfigurator;
import com.linkedin.venice.integration.utils.VeniceControllerWrapper;
import com.linkedin.venice.meta.Store;
import java.util.Arrays;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobConf;

import javax.xml.bind.DatatypeConverter;
import org.testng.annotations.DataProvider;

import static com.linkedin.venice.hadoop.KafkaPushJob.*;

public class AbstractTestVeniceMR {
  protected static final String SCHEMA_STR = "{\n" +
      "\t\"type\": \"record\",\n" +
      "\t\"name\": \"TestRecord\",\n" +
      "\t\"fields\": [\n" +
      "\t\t{\"name\": \"key\", \"type\": \"string\"},\n" +
      "\t\t{\"name\": \"value\", \"type\": \"string\"}\n" +
      "\t]\n" +
      "}";
  protected static final String KEY_FIELD = "key";
  protected static final String VALUE_FIELD = "value";
  protected static final int VALUE_SCHEMA_ID = 1;

  protected static final String TOPIC_NAME = "test_store_v1";

  protected static final String MAPPER_PARAMS_DATA_PROVIDER = "mapperParams";

  @DataProvider(name = MAPPER_PARAMS_DATA_PROVIDER)
  public static Object[][] mapperParams() {
    List<Integer> numReducersValues = Arrays.asList(1, 10, 1000);
    List<Integer> taskIdValues = Arrays.asList(0, 1, 10, 1000);
    Object[][] params = new Object[numReducersValues.size() * taskIdValues.size()][2];
    int paramsIndex = 0;
    for (int numReducers: numReducersValues) {
      for (int taskId: taskIdValues) {
        params[paramsIndex][0] = numReducers;
        params[paramsIndex++][1] = taskId;
      }
    }
    return params;
  }

  protected JobConf setupJobConf() {
    return new JobConf(getDefaultJobConfiguration());
  }

  protected Configuration getDefaultJobConfiguration() {
    Configuration config = new Configuration();
    config.set(TOPIC_PROP, TOPIC_NAME);
    config.set(KEY_FIELD_PROP, KEY_FIELD);
    config.set(VALUE_FIELD_PROP, VALUE_FIELD);
    config.set(SCHEMA_STRING_PROP, SCHEMA_STR);
    config.setInt(VALUE_SCHEMA_ID_PROP, VALUE_SCHEMA_ID);
    config.setLong(STORAGE_QUOTA_PROP, Store.UNLIMITED_STORAGE_QUOTA);
    config.setDouble(STORAGE_ENGINE_OVERHEAD_RATIO, VeniceControllerWrapper.DEFAULT_STORAGE_ENGINE_OVERHEAD_RATIO);
    config.setBoolean(ALLOW_DUPLICATE_KEY, false);
    config.set(COMPRESSION_STRATEGY, CompressionStrategy.NO_OP.toString());
    config.set(SSL_CONFIGURATOR_CLASS_CONFIG, TempFileSSLConfigurator.class.getName());
    // TODO: Remove LI-specific configs
    config.set(SSL_KEY_STORE_PROPERTY_NAME, "li.datavault.identity");
    config.set(SSL_TRUST_STORE_PROPERTY_NAME, "li.datavault.truststore");
    config.set(VeniceReducer.MAP_REDUCE_JOB_ID_PROP, "job_200707121733_0003");
    config.set(REDUCER_MINIMUM_LOGGING_INTERVAL_MS, "180000");
    return new JobConf(config);
  }

  public static String getHexString(byte[] bytes) {
    return DatatypeConverter.printHexBinary(bytes);
  }
}
