package com.linkedin.venice.storage;

import com.linkedin.venice.Common.TestUtils;
import com.linkedin.venice.exceptions.PersistenceFailureException;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.store.Store;
import org.testng.Assert;
import org.testng.annotations.Test;


/**
 * Test the operations of the Store Interface
 * 1. put, get, delete and overwriting put operations
 * 2. Test non existent Keys
 * 3. Test null keys
 * 4.
 */
public abstract class AbstractStoreTest {

  protected Store testStore;
  protected int numOfPartitions;
  int keySize = 50;
  int valueSize = 500;
  int uniqueKeyOrValueSize = 350;

  // creates instance for testStore
  public abstract void createStoreForTest()
      throws Exception;

  protected byte[] doGet(int partitionId, byte[] key)
      throws VeniceException {
    byte[] result;
    result = testStore.get(partitionId, key);
    return result;
  }

  protected void doPut(int partitionId, byte[] key, byte[] value)
      throws VeniceException {
    testStore.put(partitionId, key, value);
  }

  protected void doDelete(int partitionId, byte[] key)
      throws VeniceException {
    testStore.delete(partitionId, key);
  }


  public void testGetAndPut() {
    byte[] key = TestUtils.getRandomBytes(keySize);
    byte[] value = TestUtils.getRandomBytes(valueSize);
    int partitionId = TestUtils.getRandomIntwithin(numOfPartitions);
    byte[] foundValue;
    try {
      doPut(partitionId, key, value);
      foundValue = doGet(partitionId, key);
      Assert.assertEquals(value, foundValue,
          "The actual value: " + value.toString() + " and expected value: " + foundValue.toString() + " do not match!");
    } catch (VeniceException e) {
      Assert.fail("Exception was thrown: " + e.getMessage(), e);
    }
  }


  public void testDelete() {
    byte[] key = TestUtils.getRandomBytes(keySize);
    byte[] value = TestUtils.getRandomBytes(valueSize);
    int partitionId = TestUtils.getRandomIntwithin(numOfPartitions);
    byte[] foundValue;
    try {
      doPut(partitionId, key, value);
      foundValue = doGet(partitionId, key);
      Assert.assertEquals(value, foundValue,
          "The actual value: " + value.toString() + " and expected value: " + foundValue.toString() + " do not match!");
      doDelete(partitionId, key);
      foundValue = null;
      try {
        foundValue = doGet(partitionId, key);
        if (foundValue != null) {
          Assert.fail("Delete failed. found a value: " + foundValue.toString() + "  for the key: " + key.toString()
              + " after deletion. ");
        }
      } catch (PersistenceFailureException e) {
        // This is expected.
      }
    } catch (Exception e) {
      Assert.fail("Exception was thrown: " + e.getMessage(), e);
    }
  }

  public void testUpdate() {
    byte[] key = TestUtils.getRandomBytes(keySize);
    byte[] value = TestUtils.getRandomBytes(valueSize);
    byte[] updatedValue = TestUtils.getRandomBytes(uniqueKeyOrValueSize);
    int partitionId = TestUtils.getRandomIntwithin(numOfPartitions);
    byte[] foundValue;
    try {
      doPut(partitionId, key, value);
      foundValue = doGet(partitionId, key);
      Assert.assertEquals(value, foundValue,
          "The actual value: " + value.toString() + " and expected value: " + foundValue.toString() + " do not match!");
      doPut(partitionId, key, updatedValue);
      foundValue = doGet(partitionId, key);
      Assert.assertEquals(updatedValue, foundValue,
          "The updated value: " + updatedValue.toString() + " and expected value: " + foundValue.toString()
              + " do not match!");
    } catch (VeniceException e) {
      Assert.fail("Exception was thrown: " + e.getMessage(), e);
    }
  }

  public void testGetInvalidKeys() {
    byte[] key = TestUtils.getRandomBytes(uniqueKeyOrValueSize);
    int partitionId = TestUtils.getRandomIntwithin(numOfPartitions);
    byte[] foundValue = null;
    try {
      foundValue = doGet(partitionId, key);
      if (foundValue != null) {
        Assert.fail(
            "Get succeeded for a non Existing key. Found a value: " + foundValue.toString() + "  for the key: " + key
                .toString());
      }
    } catch (Exception e) {
      //This is expected.
    }
  }


  public void testPutNullKey() {
    byte[] key = null;
    byte[] value = TestUtils.getRandomBytes(valueSize);
    int partitionId = TestUtils.getRandomIntwithin(numOfPartitions);
    try {
      doPut(partitionId, key, value);
    } catch (IllegalArgumentException e) {
      // This is expected
      return;
    } catch (Exception e) {
      Assert.fail("Exception was thrown: " + e.getMessage(), e);
    }
    Assert.fail("Put succeeded for key: null and value: " + value.toString() + " unexpectedly");
  }
}
