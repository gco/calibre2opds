package com.gmail.dpierron.calibre.cache;
/**
 * This class is responsible for managing a cache of CachedFile
 * objects.
 *
 * As well as providing the obvious support for adding/removing testing
 * for such objects it also provides for the cache to be written
 * to file at the end of a run and reloaded at the beginning of
 * the next run.   The main purpose of this is to avoid having
 * to recalculate the CRC (which is an expensive operation) between
 * runs if it can be avoided.
 */

import com.gmail.dpierron.tools.Helper;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.BackingStoreException;

public enum CachedFileManager {
  INSTANCE;
  private final static Logger logger = Logger.getLogger(CachedFileManager.class);
  private Map<String, CachedFile> cachedFilesMap = new HashMap<String, CachedFile>();
  private File cacheFile = null;

  public void initialize() {
    cachedFilesMap = null;    // Force release any currently assigned map
    cachedFilesMap = new HashMap<String, CachedFile>();
    loadCache();
  }

  /**
   * Determine if an entry is already in the cache
   *
   * @param cf CachedFile object to check
   * @return null if not present, object otherwise
   */
  public CachedFile inCache(CachedFile cf) {
    CachedFile cf_result = cachedFilesMap.get(cf.getPath());
    if (logger.isTraceEnabled())
      logger.trace("inCache=" + (cf_result != null) + ": " + cf.getPath());
    return cf_result;
  }

  /**
   * Determine if an entry is already in the cache
   *
   * @param f File object to check
   * @return null if not present, object otherwise
   */
  public CachedFile inCache(File f) {
    CachedFile cf_result = cachedFilesMap.get(f.getPath());
    if (logger.isTraceEnabled())
      logger.trace("inCache=" + (cf_result != null) + ": " + f.getPath());
    return cf_result;
  }


  /**
   * Check the entry against the cache and if needed
   * create a new entry.
   *
   * @param cf CachedFile object representing file
   * @return A CachedFile object for the given path
   */
  public CachedFile addCachedFile(CachedFile cf) {
    String path = cf.getPath();
    CachedFile cf2 = inCache(cf);
    if (cf2 == null) {
      cf2 = new CachedFile(path);
      cachedFilesMap.put(path, cf2);
      if (logger.isTraceEnabled())
        logger.trace("Added CachedFile: " + path);
    }
    return cf2;
  }


  /**
   * Check the entry against the cache and if needed
   * create a new entry.
   *
   * @param f File object representing file
   * @return A CachedFile object for the given path
   */
  public CachedFile addCachedFile(File f) {
    String path = f.getPath();
    CachedFile cf = inCache(f);
    if (cf == null) {
      cf = new CachedFile(path);
      cachedFilesMap.put(path, cf);
      if (logger.isTraceEnabled())
        logger.trace("Added file to cache: " + path);
    }
    return cf;
  }


  /**
   * Add a file to the file cache that is a source file
   *
   * @param parent Folder that will contain the file
   * @param child  Filename
   * @return CachedFile object corresponding to file
   */
  public CachedFile addCachedFile(File parent, String child) {
    return addCachedFile(new File(parent, child));
  }

  /**
   * Remove the entry from the cache (if it is present).
   *
   * @param f File object representing file
   */
  public void removeCachedFile(File f) {
    String path = f.getPath();
    if (cachedFilesMap.containsKey(path)) {
      cachedFilesMap.remove(path);
      if (logger.isTraceEnabled())
        logger.trace("Remove CachedFile: " + path);
    } else {
      if (logger.isTraceEnabled())
        logger.trace("Remove CachedFile (not found): " + path);
    }
  }

  /**
   * Remove the entry from the cache (if it is present).
   *
   * @param cf CachedFile object representing file
   */
  public void removeCachedFile(CachedFile cf) {
    removeCachedFile((File)cf);
  }


  /**
   * Set the location for any existing cache file
   *
   * @param cf Specify the folder to hold the cache
   *           This is normally the catalog sub-folder of the target folder
   */
  public void setCacheFolder(File cf) {
    assert cf != null;    // cf must not be null
    cacheFile = new File(cf, "calibre2opds.cache");
    logger.info("CRC Cache file set to " + cacheFile.getPath());
  }

  /**
   * Save the current cache for potential later re-use
   * <p/>
   * N.B. the setCacheFolder() call must have been used
   */
  public void saveCache() {

    // Check Cache folder has been set
    if (cacheFile == null) {
      if (logger.isDebugEnabled())
        logger.debug("Aborting saveCache() as cacheFile not set");
      return;
    }

    long savedCount = 0;
    long ignoredCount = 0;
    // Open cache file
    ObjectOutputStream os = null;
    BufferedOutputStream bs = null;
    FileOutputStream fs = null;
    try {
      try {
        assert cacheFile != null : "saveCache: cacheFile should never be null at this point";
        logger.debug("STARTED Saving CRC cache to file " + cacheFile.getPath());
        fs = new FileOutputStream(cacheFile);       // Open File
        assert fs != null: "saveCache: fs should never be null at this point";
        bs = new BufferedOutputStream(fs);          // Add buffering
        assert bs != null: "saveCache: bs should never be null at this point";
        os = new ObjectOutputStream(bs);            // Add object handling
        assert os != null: "saveCache: os should never be null at this point";

        // Write out the cache entries
        for (Map.Entry<String, CachedFile> m : cachedFilesMap.entrySet()) {
          CachedFile cf = m.getValue();
          String key = m.getKey();

          // We are only interested in caching entries for which the CRC is known
          // as this is the expensive operation we do not want to do unnecessarily
          if (!cf.isCrc()) {
            if (logger.isTraceEnabled())
              logger.trace("CRC not known.  Not saving CachedFile " + key);
            ignoredCount++;
          } else {
            // We only want to cache items that have actually been used this time
            // around, so ignore entries that indicate cached values not used.
            if (cf.isCached()) {
              if (logger.isTraceEnabled())
                logger.trace("Not used.  Not saving CachedFile " + key);
              ignoredCount++;
            } else {
              // No point in caching entries for non-existent files
              if (!cf.exists()) {
                if (logger.isTraceEnabled())
                  logger.trace("Not exists.  Not saving CachedFile " + key);
                ignoredCount++;
              } else {
                os.writeObject(cf);
                if (logger.isTraceEnabled())
                  logger.trace("Saved " + key);
                savedCount++;
              }
            }
          }
        }
      } finally {
        try {
          if (os != null) os.close();
          if (bs != null) bs.close();
          if (fs != null) fs.close();
        } catch (IOException e) {
          // Do nothing - we ignore an error at this point
          // Having said that, an error here is a bit unexpected so lets log it when testing
          logger.debug("saveCache: Unexpected error\n" + e);
        }
      }
    } catch (IOException e) {
      logger.warn("Exception trying to write cache: " + e);
    }

    logger.debug("Cache Entries Saved:   " + savedCount);
    logger.debug("Cache Entries Ignored: " + ignoredCount);
    logger.debug("COMPLETED Saving CRC cache to file " + cacheFile.getPath());
  }

  /**
   * Initialize the cache if there is a saved one present
   * <p/>
   * N.B. the setCacheFolder() call must have been used
   */
  public void loadCache() {

    // Check cache folder has been specified
    if (cacheFile == null) {
      if (logger.isTraceEnabled())
        logger.trace("Aborting loadCache() as cache folder not set");
      return;
    }

    if (!cacheFile.exists()) {
      if (logger.isDebugEnabled())
        logger.debug("Exiting loadCache() as cache file not present");
      return;
    }
    // Open Cache file
    ObjectInputStream os;
    FileInputStream fs;
    BufferedInputStream bs;
    long loadedCount = 0;
    try {
      logger.info("STARTED Loading CRC cache from file " + cacheFile.getPath());
      fs = new FileInputStream(cacheFile);   // Open file
      bs = new BufferedInputStream(fs);      // Add buffering
      os = new ObjectInputStream(bs);        // And now object handling
    } catch (IOException e) {
      logger.warn("Aborting loadCache() as cache file failed to open");
      // Abort any cache loading
      return;
    }

    // Read in entries from cache
    try {
      try {
        for (; ; ) {
          CachedFile cf;
          cf = (CachedFile) os.readObject();

          String path = cf.getPath();
          if (logger.isTraceEnabled())
            logger.trace("Loaded cached object " + path);
          loadedCount++;
          CachedFile cf2 = inCache(cf);
          if (cf2 == null) {
            // Not in cache, so simply add it and
            // set indicator that values not yet checked
            cf.setCached();
            addCachedFile(cf);
            if (logger.isTraceEnabled())
              logger.trace("added entry to cache");
          } else {
            // Already in cache (can this happen?), so we
            // need to determine what values (if any) can
            // be set in the entry already there.
            logger.debug("Entry already in cache - ignore cached entry for now");
          }
        }
      } finally {
        // Close cache file
        if (os != null) os.close();
        if (bs != null) bs.close();
        if (fs != null) fs.close();
      }
    } catch (java.io.EOFException io) {
      logger.trace("End of Cache file encountered");
      // Do nothing else - this is expected
    } catch (java.io.InvalidClassException ic) {
      logger.debug("Cache ignored as CachedFile class changed since it was created");
      // Should just mean that CachedFile class was changed so old cache invalid
    } catch (ClassNotFoundException cnfe) {
      logger.error("", cnfe);
    } catch (IOException e) {
      // This is to catch any currently unexpected error cnditions
      logger.warn("Exception trying to read cache: " + e);
    }

    logger.info("Cache Entries Loaded: " + loadedCount);
    logger.info("COMPLETED Loading CRC cache from file " + cacheFile.getPath());
  }

  /**
   * Delete any existing cache file
   */
  public void deleteCache() {
    if (cacheFile == null) {
      if (logger.isDebugEnabled())
        logger.debug("Aborting deleteCache() as cache folder not set");
      return;
    }
    Helper.delete(cacheFile);
    if (logger.isDebugEnabled())
      logger.debug("Deleted CRC cache file " + cacheFile.getPath());
    cacheFile = null;
  }

}
