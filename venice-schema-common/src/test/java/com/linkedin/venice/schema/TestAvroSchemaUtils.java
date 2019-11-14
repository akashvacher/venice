package com.linkedin.venice.schema;

import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.utils.AvroSchemaUtils;
import org.apache.avro.Schema;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestAvroSchemaUtils {

  @Test
  public void testWithDifferentDocField() {
    String schemaStr1 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"company\",\"type\":\"string\"}]}";
    String schemaStr2 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"company\",\"type\":\"string\", \"doc\": \"company name here\"}]}";
    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    Assert.assertTrue(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));

    Schema s3 = AvroSchemaUtils.generateSuperSetSchema(s1, s2);
    Assert.assertNotNull(s3);
  }

  @Test
  public void testCompareWithDifferentOrderFields() {
    String schemaStr1 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"company\",\"type\":\"string\"}]}";
    String schemaStr2 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"company\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"name\",\"type\":\"string\"}]}";


    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2);
    Assert.assertTrue(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1, s2));
  }

  @Test
  public void testCompareWithDifferentOrderFieldsNested() {
    String recordSchemaStr1 = "{\n" +
        "  \"type\" : \"record\",\n" +
        "  \"name\" : \"testRecord\",\n" +
        "  \"namespace\" : \"com.linkedin.avro\",\n" +
        "  \"fields\" : [ {\n" +
        "    \"name\" : \"hits\",\n" +
        "    \"type\" : {\n" +
        "      \"type\" : \"array\",\n" +
        "      \"items\" : {\n" +
        "        \"type\" : \"record\",\n" +
        "        \"name\" : \"JobAlertHit\",\n" +
        "        \"fields\" : [ {\n" +
        "          \"name\" : \"memberId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        }, {\n" +
        "          \"name\" : \"searchId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        } ]\n"
        + "      }\n" +
        "    },\n" +
        "    \"default\" : [ ]\n" +
        "  }, {\n" +
        "    \"name\" : \"hasNext\",\n" +
        "    \"type\" : \"boolean\",\n" +
        "    \"default\" : false\n" +
        "  } ]\n" +
        "}";
    String recordSchemaStr2 = "{\n" +
        "  \"type\" : \"record\",\n" +
        "  \"name\" : \"testRecord\",\n" +
        "  \"namespace\" : \"com.linkedin.avro\",\n" +
        "  \"fields\" : [ {\n" +
        "    \"name\" : \"hits\",\n" +
        "    \"type\" : {\n" +
        "      \"type\" : \"array\",\n" +
        "      \"items\" : {\n" +
        "        \"type\" : \"record\",\n" +
        "        \"name\" : \"JobAlertHit\",\n" +
        "        \"fields\" : [ {\n" +
        "          \"name\" : \"searchId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        }, {\n" +
        "          \"name\" : \"memberId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        } ]\n"
        + "      }\n" +
        "    },\n" +
        "    \"default\" : [ ]\n" +
        "  }, {\n" +
        "    \"name\" : \"hasNext\",\n" +
        "    \"type\" : \"boolean\",\n" +
        "    \"default\" : false\n" +
        "  } ]\n" +
        "}";


    Schema s1 = Schema.parse(recordSchemaStr1);
    Schema s2 = Schema.parse(recordSchemaStr2);
    Assert.assertTrue(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1, s2));
  }

  @Test (expectedExceptions = VeniceException.class)
  public void testWithIncompatibleSchema() {
    String schemaStr1 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"company\",\"type\":\"string\"}]}";
    String schemaStr2 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"int\",\"doc\":\"name field\"},{\"name\":\"company\",\"type\":\"string\", \"doc\": \"company name here\"}]}";
    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);

    Assert.assertFalse(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));
    AvroSchemaUtils.generateSuperSetSchema(s1, s2);
  }

  @Test (expectedExceptions = VeniceException.class)
  public void testWithEnumEvolution() {
    String schemaStr1 = "{\n" +
        "           \"type\": \"record\",\n" +
        "           \"name\": \"KeyRecord\",\n" +
        "           \"fields\" : [\n" +
        "               {\"name\": \"name\", \"type\": \"string\", \"doc\": \"name field\"},\n" +
        "               {\"name\": \"company\", \"type\": \"string\"},\n" +
        "               {\n" +
        "                 \"name\": \"Suit\", \n" +
        "                 \"type\": {\n" +
        "                        \"name\": \"SuitType\", \"type\": \"enum\", \"symbols\": [\"SPADES\", \"DIAMONDS\", \"HEART\", \"CLUBS\"]\n" +
        "                } \n" +
        "              }\n" +
        "           ]\n" +
        "        }";
    String schemaStr2 = "{\n" +
        "           \"type\": \"record\",\n" +
        "           \"name\": \"KeyRecord\",\n" +
        "           \"fields\" : [\n" +
        "               {\"name\": \"name\", \"type\": \"string\", \"doc\": \"name field\"},\n" +
        "               {\"name\": \"company\", \"type\": \"string\"},\n" +
        "               {\n" +
        "                 \"name\": \"Suit\", \n" +
        "                 \"type\": {\n" +
        "                        \"name\": \"SuitType\", \"type\": \"enum\", \"symbols\": [\"SPADES\", \"DIAMONDS\", \"CLUBS\"]\n" +
        "                } \n" +
        "              }\n" +
        "           ]\n" +
        "        }";
    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);

    Assert.assertFalse(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));
    AvroSchemaUtils.generateSuperSetSchema(s1, s2);
  }

  @Test
  public void testSchemaMerge() {
    String schemaStr1 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"company\",\"type\":\"string\"}]}";
    String schemaStr2 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"business\",\"type\":\"string\"}]}";

    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    Assert.assertFalse(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));
    Schema s3 = AvroSchemaUtils.generateSuperSetSchema(s1, s2);
    Assert.assertNotNull(s3);
  }

  @Test
  public void testSchemaMergeUnion() {
    String schemaStr1 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"experience\",\"type\":[\"int\", \"float\", \"null\"], \"default\" : 32},{\"name\":\"company\",\"type\":\"string\"}]}";
    String schemaStr2 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"experience\",\"type\":[\"string\", \"int\", \"null\"], \"default\" : \"dflt\"},{\"name\":\"organization\",\"type\":\"string\"}]}";

    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    Assert.assertFalse(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));

    Schema s3 = AvroSchemaUtils.generateSuperSetSchema(s1, s2);
    Assert.assertNotNull(s3.getField("company"));
    Assert.assertNotNull(s3.getField("organization"));
  }

  @Test
  public void testSchemaMergeFields() {
    String schemaStr1 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"id1\",\"type\":\"double\"}]}";
    String schemaStr2 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"id2\",\"type\":\"int\"}]}";

    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    Assert.assertFalse(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));

    Schema s3 = AvroSchemaUtils.generateSuperSetSchema(s1, s2);
    Assert.assertNotNull(s3.getField("id1"));
    Assert.assertNotNull(s3.getField("id2"));
  }

  @Test
  public void testWithNewFieldArrayRecord() {
    String recordSchemaStr1 = "{\n" +
        "  \"type\" : \"record\",\n" +
        "  \"name\" : \"testRecord\",\n" +
        "  \"namespace\" : \"com.linkedin.avro\",\n" +
        "  \"fields\" : [ {\n" +
        "    \"name\" : \"hits\",\n" +
        "    \"type\" : {\n" +
        "      \"type\" : \"array\",\n" +
        "      \"items\" : {\n" +
        "        \"type\" : \"record\",\n" +
        "        \"name\" : \"JobAlertHit\",\n" +
        "        \"fields\" : [ {\n" +
        "          \"name\" : \"memberId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        }, {\n" +
        "          \"name\" : \"searchId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        } ]\n"
        + "      }\n" +
        "    },\n" +
        "    \"default\" : [ ]\n" +
        "  }, {\n" +
        "    \"name\" : \"hasNext\",\n" +
        "    \"type\" : \"boolean\",\n" +
        "    \"default\" : false\n" +
        "  } ]\n" +
        "}";

    String recordSchemaStr2 = "{\n" +
        "  \"type\" : \"record\",\n" +
        "  \"name\" : \"testRecord\",\n" +
        "  \"namespace\" : \"com.linkedin.avro\",\n" +
        "  \"fields\" : [ {\n" +
        "    \"name\" : \"hits\",\n" +
        "    \"type\" : {\n" +
        "      \"type\" : \"array\",\n" +
        "      \"items\" : {\n" +
        "        \"type\" : \"record\",\n" +
        "        \"name\" : \"JobAlertHit\",\n" +
        "        \"fields\" : [ {\n" +
        "          \"name\" : \"memberId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        }, {\n" +
        "          \"name\" : \"companyId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        }, {\n" +
        "          \"name\" : \"searchId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        } ]\n"
        + "      }\n" +
        "    },\n" +
        "    \"default\" : [ ]\n" +
        "  }, {\n" +
        "    \"name\" : \"hasNext\",\n" +
        "    \"type\" : \"boolean\",\n" +
        "    \"default\" : false\n" +
        "  } ]\n" +
        "}";
    Schema s1 = Schema.parse(recordSchemaStr1);
    Schema s2 = Schema.parse(recordSchemaStr2);
    Assert.assertFalse(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));

    Schema s3 = AvroSchemaUtils.generateSuperSetSchema(s1, s2);
    Assert.assertNotNull(s3);
  }

  @Test
  public void tesMergeWithDefaultValueUpdate() {
    String schemaStr1 = "{\n" +
        "           \"type\": \"record\",\n" +
        "           \"name\": \"KeyRecord\",\n" +
        "           \"fields\" : [\n" +
        "               {\"name\": \"name\", \"type\": \"string\", \"doc\": \"name field\"},\n" +
        "               {\"name\": \"company\", \"type\": \"string\"},\n" +
        "               {\n" +
        "                 \"name\": \"Suit\", \n" +
        "                 \"type\": {\n" +
        "                        \"name\": \"SuitType\", \"type\": \"enum\", \"symbols\": [\"SPADES\", \"DIAMONDS\", \"HEART\", \"CLUBS\"]\n" +
        "                }\n" +
        "              },\n" +
        "               {\"name\": \"salary\", \"type\": \"long\", \"default\" : 123}\n" +
        "           ]\n" +
        "        }";
    String schemaStr2 = "{\n" +
        "           \"type\": \"record\",\n" +
        "           \"name\": \"KeyRecord\",\n" +
        "           \"fields\" : [\n" +
        "               {\"name\": \"name\", \"type\": \"string\", \"doc\": \"name field\"},\n" +
        "               {\"name\": \"company\", \"type\": \"string\"},\n" +
        "               {\n" +
        "                 \"name\": \"Suit\", \n" +
        "                 \"type\": {\n" +
        "                        \"name\": \"SuitType\", \"type\": \"enum\", \"symbols\": [\"SPADES\", \"DIAMONDS\", \"HEART\", \"CLUBS\"]\n" +
        "                } \n" +
        "              },\n" +
        "               {\"name\": \"salary\", \"type\": \"long\", \"default\": 123}" +
        "           ]\n" +
        "        }";

    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    Assert.assertTrue(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));

    Schema s3 = AvroSchemaUtils.generateSuperSetSchema(s1, s2);
    Assert.assertNotNull(s3.getField("salary").defaultValue());
  }

  @Test
  public void testDocChange() {
    String schemaStr1 = "{\n" +
        "           \"type\": \"record\",\n" +
        "           \"name\": \"KeyRecord\",\n" +
        "           \"fields\" : [\n" +
        "               {\"name\": \"name\", \"type\": \"string\", \"doc\": \"name field\"},\n" +
        "               {\"name\": \"company\", \"type\": \"string\"},\n" +
        "               {\n" +
        "                 \"name\": \"Suit\", \n" +
        "                 \"type\": {\n" +
        "                        \"name\": \"SuitType\", \"type\": \"enum\", \"symbols\": [\"SPADES\", \"DIAMONDS\", \"HEART\", \"CLUBS\"]\n" +
        "                }\n" +
        "              },\n" +
        "               {\"name\": \"salary\", \"type\": \"long\"}\n" +
        "           ]\n" +
        "        }";
    String schemaStr2 = "{\n" +
        "           \"type\": \"record\",\n" +
        "           \"name\": \"KeyRecord\",\n" +
        "           \"fields\" : [\n" +
        "               {\"name\": \"name\", \"type\": \"string\", \"doc\": \"name field\"},\n" +
        "               {\"name\": \"company\", \"type\": \"string\"},\n" +
        "               {\n" +
        "                 \"name\": \"Suit\", \n" +
        "                 \"type\": {\n" +
        "                        \"name\": \"SuitType\", \"type\": \"enum\", \"symbols\": [\"SPADES\", \"DIAMONDS\", \"HEART\", \"CLUBS\"]\n" +
        "                }\n" +
        "              },\n" +
        "               {\"name\": \"salary\", \"type\": \"long\"}\n" +
        "           ]\n" +
        "        }";
    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    Assert.assertTrue(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));

    Assert.assertTrue(s1.equals(s2));
    Assert.assertFalse(AvroSchemaUtils.hasDocFieldChange(s1, s2));
  }

  @Test
  public void testSchemaUnionDocUpdate() {
    String schemaStr1 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field\"},{\"name\":\"experience\",\"type\":[\"int\", \"float\", \"null\"], \"default\" : 32, \"doc\" : \"doc\"},{\"name\":\"company\",\"type\":\"string\"}]}";
    String schemaStr2 = "{\"type\":\"record\",\"name\":\"KeyRecord\",\"fields\":[{\"name\":\"name\",\"type\":\"string\",\"doc\":\"name field1\"},{\"name\":\"experience\",\"type\":[\"int\", \"float\", \"null\"], \"default\" : 32, \"doc\" : \"doc\"},{\"name\":\"company\",\"type\":\"string\"}]}";

    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    Assert.assertTrue(s1.equals(s2));
    Assert.assertTrue(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));

    Assert.assertTrue(AvroSchemaUtils.hasDocFieldChange(s1, s2));
  }

  @Test
  public void testSchemaArrayDocUpdate() {
    String schemaStr1 = "{\n" +
        "  \"type\" : \"record\",\n" +
        "  \"name\" : \"testRecord\",\n" +
        "  \"namespace\" : \"com.linkedin.avro\",\n" +
        "  \"fields\" : [ {\n" +
        "    \"name\" : \"hits\",\n" +
        "    \"type\" : {\n" +
        "      \"type\" : \"array\",\n" +
        "      \"items\" : {\n" +
        "        \"type\" : \"record\",\n" +
        "        \"name\" : \"JobAlertHit\",\n" +
        "        \"fields\" : [ {\n" +
        "          \"name\" : \"memberId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        }, {\n" +
        "          \"name\" : \"searchId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        } ], \n" +
        "        \"doc\" : \"record doc\" \n" +
        "      }\n" +
        "    },\n" +
        "    \"default\" : [ ]\n" +
        "  }, {\n" +
        "    \"name\" : \"hasNext\",\n" +
        "    \"type\" : \"boolean\",\n" +
        "    \"default\" : false\n" +
        "  } ]\n" +
        "}";

    String schemaStr2 = "{\n" +
        "  \"type\" : \"record\",\n" +
        "  \"name\" : \"testRecord\",\n" +
        "  \"namespace\" : \"com.linkedin.avro\",\n" +
        "  \"fields\" : [ {\n" +
        "    \"name\" : \"hits\",\n" +
        "    \"type\" : {\n" +
        "      \"type\" : \"array\",\n" +
        "      \"items\" : {\n" +
        "        \"type\" : \"record\",\n" +
        "        \"name\" : \"JobAlertHit\",\n" +
        "        \"fields\" : [ {\n" +
        "          \"name\" : \"memberId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        }, {\n" +
        "          \"name\" : \"searchId\",\n" +
        "          \"type\" : \"long\"\n" +
        "        } ]\n"
        + "      }\n" +
        "    },\n" +
        "    \"default\" : [ ]\n" +
        "  }, {\n" +
        "    \"name\" : \"hasNext\",\n" +
        "    \"type\" : \"boolean\",\n" +
        "    \"default\" : false\n" +
        "  } ]\n" +
        "}";

    Schema s1 = Schema.parse(schemaStr1);
    Schema s2 = Schema.parse(schemaStr2);
    Assert.assertTrue(s1.equals(s2));
    Assert.assertTrue(AvroSchemaUtils.compareSchemaIgnoreFieldOrder(s1,s2));

    Assert.assertTrue(AvroSchemaUtils.hasDocFieldChange(s1, s2));
  }
}
