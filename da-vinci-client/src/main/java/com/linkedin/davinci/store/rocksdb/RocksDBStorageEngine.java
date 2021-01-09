package com.linkedin.davinci.store.rocksdb;

import com.linkedin.davinci.config.VeniceStoreConfig;
import com.linkedin.venice.exceptions.VeniceException;
import com.linkedin.venice.kafka.protocol.state.PartitionState;
import com.linkedin.venice.kafka.protocol.state.StoreVersionState;
import com.linkedin.venice.meta.PersistenceType;
import com.linkedin.venice.serialization.avro.InternalAvroSpecificSerializer;
import com.linkedin.davinci.stats.RocksDBMemoryStats;
import com.linkedin.davinci.store.AbstractStorageEngine;
import com.linkedin.davinci.store.StoragePartitionConfig;
import com.linkedin.venice.store.rocksdb.RocksDBUtils;
import com.linkedin.venice.utils.Utils;
import com.linkedin.venice.utils.VeniceProperties;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import static com.linkedin.davinci.store.rocksdb.RocksDBServerConfig.*;


class RocksDBStorageEngine extends AbstractStorageEngine<RocksDBStoragePartition> {
  private static final Logger LOGGER = Logger.getLogger(RocksDBStorageEngine.class);

  public static final String SERVER_CONFIG_FILE_NAME = "rocksdbConfig";

  private final String rocksDbPath;
  private final String storeDbPath;
  private final RocksDBMemoryStats memoryStats;
  private final RocksDBThrottler rocksDbThrottler;
  private final RocksDBServerConfig rocksDBServerConfig;
  private final RocksDBStorageEngineFactory factory;
  private final VeniceStoreConfig storeConfig;


  public RocksDBStorageEngine(VeniceStoreConfig storeConfig,
                              RocksDBStorageEngineFactory factory,
                              String rocksDbPath,
                              RocksDBMemoryStats rocksDBMemoryStats,
                              RocksDBThrottler rocksDbThrottler,
                              RocksDBServerConfig rocksDBServerConfig,
                              InternalAvroSpecificSerializer<StoreVersionState> storeVersionStateSerializer,
                              InternalAvroSpecificSerializer<PartitionState> partitionStateSerializer) {
    super(storeConfig.getStoreName(), storeVersionStateSerializer, partitionStateSerializer);
    this.storeConfig = storeConfig;
    this.rocksDbPath = rocksDbPath;
    this.memoryStats = rocksDBMemoryStats;
    this.rocksDbThrottler = rocksDbThrottler;
    this.rocksDBServerConfig = rocksDBServerConfig;
    this.factory = factory;


    // Create store folder if it doesn't exist
    storeDbPath = RocksDBUtils.composeStoreDbDir(this.rocksDbPath, getName());
    File storeDbDir = new File(storeDbPath);
    if (!storeDbDir.exists()) {
      storeDbDir.mkdirs();
      LOGGER.info("Created RocksDb dir for store: " + getName());
    } else {
      if (storeConfig.isRocksDbStorageEngineConfigCheckEnabled()) {
        // We only validate it when re-opening the storage engine.
        if (hasConflictPersistedStoreEngineConfig()) {
          try {
            LOGGER.info("Removing store directory: " + storeDbDir.getAbsolutePath());
            FileUtils.deleteDirectory(storeDbDir);
          } catch (IOException e) {
            throw new VeniceException("Encounter IO exception when removing RocksDB engine folder.", e);
          }
          storeDbDir.mkdirs();
        }
      }
    }

    // restoreStoragePartitions will create metadata partition if not exist.
    restoreStoragePartitions(storeConfig.isRestoreMetadataPartition(), storeConfig.isRestoreDataPartitions());

    if (storeConfig.isRestoreMetadataPartition()) {
      // Persist RocksDB table format option used in building the storage engine.
      persistStoreEngineConfig();
    }
  }

  @Override
  public PersistenceType getType() {
    return PersistenceType.ROCKS_DB;
  }

  @Override
  protected Set<Integer> getPersistedPartitionIds() {
    File storeDbDir = new File(storeDbPath);
    if (!storeDbDir.exists()) {
      LOGGER.info("Store dir: " + storeDbPath + " doesn't exist");
      return new HashSet<>();
    }
    if (!storeDbDir.isDirectory()) {
      throw new VeniceException("Store dir: " + storeDbPath + " is not a directory!!!");
    }
    String[] partitionDbNames = storeDbDir.list();
    HashSet<Integer> partitionIdSet = new HashSet<>();
    for (String partitionDbName : partitionDbNames) {
      partitionIdSet.add(RocksDBUtils.parsePartitionIdFromPartitionDbName(partitionDbName));
    }

    return partitionIdSet;
  }

  @Override
  public RocksDBStoragePartition createStoragePartition(StoragePartitionConfig storagePartitionConfig) {
    return new RocksDBStoragePartition(storagePartitionConfig, factory, rocksDbPath, memoryStats, rocksDbThrottler, rocksDBServerConfig);
  }

  @Override
  public void drop() {
    super.drop();

    // Whoever is in control of the metadata partition should be responsible of dropping the storage engine folder.
    if (storeConfig.isRestoreMetadataPartition()) {
      // Remove store db dir
      File storeDbDir = new File(storeDbPath);
      if (storeDbDir.exists()) {
        LOGGER.info("Started removing database dir: " + storeDbPath + " for store: " + getName());
        if (!storeDbDir.delete()) {
          LOGGER.warn("Failed to remove dir: " + storeDbDir);
        } else {
          LOGGER.info("Finished removing database dir: " + storeDbPath + " for store: " + getName());
        }
      }
    }
  }

  @Override
  public long getStoreSizeInBytes() {
    File storeDbDir = new File(storeDbPath);
    if (storeDbDir.exists()) {
      /**
       * {@link FileUtils#sizeOf(File)} will throw {@link IllegalArgumentException} if the file/dir doesn't exist.
       */
      return FileUtils.sizeOf(storeDbDir);
    } else {
      return 0;
    }
  }

  private boolean hasConflictPersistedStoreEngineConfig() {
    String configPath = getRocksDbEngineConfigPath();
    File storeEngineConfig = new File(configPath);
    if (storeEngineConfig.exists()) {
      LOGGER.info("RocksDB storage engine config found at " + configPath);
      try {
        VeniceProperties persistedStorageEngineConfig = Utils.parseProperties(storeEngineConfig);
        LOGGER.info("Found storage engine configs: " + persistedStorageEngineConfig.toString(true));
        boolean usePlainTableFormat = persistedStorageEngineConfig.getBoolean(ROCKSDB_PLAIN_TABLE_FORMAT_ENABLED, true);
        if (usePlainTableFormat != rocksDBServerConfig.isRocksDBPlainTableFormatEnabled()) {
          String existingTableFormat = usePlainTableFormat ? "PlainTable" : "BlockBasedTable";
          String newTableFormat = rocksDBServerConfig.isRocksDBPlainTableFormatEnabled() ? "PlainTable" : "BlockBasedTable";
          LOGGER.warn("Tried to open an existing " + existingTableFormat + " RocksDB format engine with table format option: "
              + newTableFormat + ". Will remove the content and recreate the folder.");
          return true;
        }
      } catch (IOException e) {
        throw new VeniceException("Encounter IO exception when validating RocksDB engine configs.", e);
      }
    } else {
      // If no existing config is found, we will by default skip the checking as not enough information is given to enforce the check.
      LOGGER.warn("RocksDB storage engine config not found for store" + getName() + " skipping the validation.");
    }
    return false;
  }

  private void persistStoreEngineConfig() {
    String configPath = getRocksDbEngineConfigPath();
    File storeEngineConfig = new File(configPath);
    if (storeEngineConfig.exists()) {
      LOGGER.warn("RocksDB engine already exists, will skipp persisting config.");
      return;
    }
    try {
      storeConfig.getPersistStorageEngineConfig().storeFlattened(storeEngineConfig);
    } catch (IOException e) {
      throw new VeniceException("Unable to persist store engine config.", e);
    }
  }

  private String getRocksDbEngineConfigPath() {
    return RocksDBUtils.composePartitionDbDir(rocksDbPath, getName(), METADATA_PARTITION_ID) + "/" + SERVER_CONFIG_FILE_NAME;
  }
 }