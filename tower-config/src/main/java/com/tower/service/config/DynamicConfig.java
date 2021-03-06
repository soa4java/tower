package com.tower.service.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.XMLPropertiesConfiguration;

import com.dangdang.config.service.GeneralConfigGroup;
import com.dangdang.config.service.file.FileConfigGroup;
import com.dangdang.config.service.file.FileConfigProfile;
import com.dangdang.config.service.observer.IObserver;
import com.dangdang.config.service.zookeeper.ZookeeperConfigGroup;
import com.dangdang.config.service.zookeeper.ZookeeperConfigProfile;
import com.tower.service.config.dict.ConfigFileDict;
import com.tower.service.config.dict.ConfigFileTypeDict;
import com.tower.service.config.utils.TowerConfig;
import com.tower.service.log.Logger;
import com.tower.service.log.LoggerFactory;
import com.tower.service.util.Constants;
import com.tower.service.util.MD5Util;
import com.tower.service.util.StringUtil;

/**
 * 动态检测&加载变化内容<br>
 * global.config.dir：配置文件路径<br>
 * 默认为没有profile<br>
 * 当没有设置profile时 <br>
 * System.getProperty(GLOBAL_CONFIG_DIR, GLOBAL_CONFIG_DIR_DEF) + File.separator
 * + _settingFileName + File.separator + "." + type; <br>
 * 当设置了profile时 <br>
 * System.getProperty(GLOBAL_CONFIG_DIR, GLOBAL_CONFIG_DIR_DEF) + File.separator
 * + profiel+.+ _settingFileName + File.separator + "." + type;
 * 
 * 支持加密机制:通过key是否含有.encrypted来支持加密支持
 * 
 * @author alexzhu
 * 
 */
public class DynamicConfig implements ConfigFileDict, Constants, Configuration,
		IConfigListener, IObserver {

	protected static Logger loggers = LoggerFactory
			.getLogger(DynamicConfig.class);

	protected Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * 默认为没有profile<br>
	 * 当没有设置profile时 <br>
	 * System.getProperty(GLOBAL_CONFIG_DIR, GLOBAL_CONFIG_DIR_DEF) +
	 * File.separator + _settingFileName + File.separator + "." + type; <br>
	 * 当设置了profile时 <br>
	 * System.getProperty(GLOBAL_CONFIG_DIR, GLOBAL_CONFIG_DIR_DEF) +
	 * File.separator + profiel+.+ _settingFileName + File.separator + "." +
	 * type;
	 */
	private String _settingFileName = "common";
	private String type = ConfigFileTypeDict.XML;
	private Configuration delegate;
	private String encoding = "utf-8";
	private boolean delimiterParsingDisabled;
	private List<String> configFiles = new ArrayList<String>();
	private GeneralConfigGroup group = null;
	private FileConfigProfile profile = null;
	private static ZookeeperConfigProfile zkProfile = null;
	private static ZookeeperConfigProfile appZKProfile = null;
	private String zkServers = null;
	private String ST_Type = "zookeeper";

	public DynamicConfig() {
		logger = LoggerFactory.getLogger(getClass());
		try {
			if (isZookeeper() && zkProfile == null) {
				zkProfile = createZookeeperProfile("/config");
			}
			if (isZookeeper() && !StringUtil.isEmpty(getAppHomeDir())) {
				if (appZKProfile == null) {
					appZKProfile = createZookeeperProfile(getAppHomeDir()
							+ File.separator + "config");
				}
			}
		} catch (Exception ex) {
			logger.error(ex);
		}
	}
	
	public boolean isZookeeper(){
		return Boolean.valueOf(System.getProperty("config.store.type", "false"));
	}

	/**
	 * 
	 * @param fileStoreType
	 *            file:本地,http:http服务,https:
	 */
	public DynamicConfig(String fileName, String type) {
		this.setFileName(fileName);
		this.setType(type);
	}

	@PostConstruct
	public void init() {

		Map tmp = null;
		this.group = $build();
		if(group!=null){
			tmp = new HashMap(group);
		}
		else{
			tmp = new HashMap();
		}
		MapConfiguration config = new MapConfiguration(tmp);
		delegate = new SecutiryCompositeConfiguration(config);
		group.register(this);
	}

	// this.regist();
	// delegate = this.build();
	// ConfigUtil.getConfigUtilsInstance(storeType).addListener(this);
	// }

	public GeneralConfigGroup $build() {
		GeneralConfigGroup group = null;
		profile = createFileProfile(this.getType());
		/**
		 * 开发配置
		 */
		String file = null;
		try {
			file = this.getFullFileName("classpath:META-INF/config/local"
					+ File.separator + "%s");
			group = new FileConfigGroup(profile, file);
		} catch (Exception ex) {
			logger.info("配置文件'" + file + "'没有找到");
		}
		if (!StringUtil.isEmpty(getProfile())) {
			try {
				this.getFullFileName("classpath:META-INF/config/local/"
						+ getProfile() + "%s");
				group = new FileConfigGroup(group, profile, file);
			} catch (Exception ex) {
				logger.info("配置文件'" + file + "'没有找到");
			}
		}
		/**
		 * 部署默认配置
		 */
		try {
			file = this.getFullFileName("/config" + File.separator + "%s");
			group = new FileConfigGroup(group, profile, file);
		} catch (Exception ex) {
			logger.info("配置文件'" + file + "'没有找到");
		}
		if (!StringUtil.isEmpty(getProfile())) {
			try {
				file = this.getFullFileName("/config/" + getProfile() + "%s");
				group = new FileConfigGroup(group, profile, file);
			} catch (Exception ex) {
				logger.info("配置文件'" + file + "'没有找到");
			}
		}
		/**
		 * 部署默认［zk配置］
		 */
		// if (ST_ZK.equalsIgnoreCase(storeType)) {
		if(isZookeeper()){
			try {
				group = new ZookeeperConfigGroup(group, zkProfile, _settingFileName);
				if (!StringUtil.isEmpty(getProfile())) {
					group = new ZookeeperConfigGroup(group, zkProfile, getProfile()
							+ _settingFileName);
				}
			} catch (Exception ex) {
				logger.info(zkServers + "/config/" + _settingFileName
						+ " zookeeper配置没有找到");
			}
		}
		// }
		/**
		 * 应用配置
		 */
		if (!StringUtil.isEmpty(getAppHomeDir())) {
			/**
			 * 文件配置
			 */
			try {
				file = this.getFullFileName(getAppHomeDir() + File.separator
						+ "config" + File.separator + "%s");
				group = new FileConfigGroup(group, profile, file);
			} catch (Exception ex) {
				logger.info("配置文件'" + file + "'没有找到");
			}
			if (!StringUtil.isEmpty(getProfile())) {
				try {
					file = this.getFullFileName(getAppHomeDir()
							+ File.separator + "config" + File.separator
							+ getProfile() + "%s");
					group = new FileConfigGroup(group, profile, file);
				} catch (Exception ex) {
					logger.info("配置文件'" + file + "'没有找到");
				}
			}
			/**
			 * zk配置
			 */
			// if (ST_ZK.equalsIgnoreCase(storeType)) {
			if(isZookeeper()){
				try {
					group = new ZookeeperConfigGroup(group, appZKProfile,
							_settingFileName);
					if (!StringUtil.isEmpty(getProfile())) {
						group = new ZookeeperConfigGroup(group, appZKProfile,
								getProfile() + _settingFileName);
					}
				} catch (Exception ex) {
					logger.info(zkServers + getAppHomeDir() + File.separator
							+ "config" + _settingFileName + " zookeeper配置没有找到");
				}
			}
			// }
		}
		return group;
	}

	private String getFullFileName(String pattern) {
		if (this.getType() != null
				&& this.getType().trim().equalsIgnoreCase("properties")) {
			pattern = new StringBuffer(pattern).append(".properties")
					.toString();
		} else {
			pattern = new StringBuffer(pattern).append(".xml").toString();
		}
		String location = String.format(pattern, this._settingFileName);
		return location;
	}

	private FileConfigProfile createFileProfile(String pattern) {
		FileConfigProfile fileConfigProfile = null;
		if (this.getType() != null
				&& this.getType().trim().equalsIgnoreCase("properties")) {
			pattern = new StringBuffer(pattern).append(".properties")
					.toString();
			fileConfigProfile = new FileConfigProfile("UTF8", "properties");
		} else {
			pattern = new StringBuffer(pattern).append(".xml").toString();
			fileConfigProfile = new FileConfigProfile("UTF8", "xml");
		}
		return fileConfigProfile;
	}

	private static ZookeeperConfigProfile createZookeeperProfile(String pattern) {
		ZookeeperConfigProfile configProfile = new ZookeeperConfigProfile(
				TowerConfig.getConfig("config.store.type.zookeeper.addresses",
						"127.0.0.0:2181"), pattern, "1.0.0");
		return configProfile;
	}

	private long lastmodify = 0;

	@Override
	public void notified(String data, String value) {
		if (lastmodify != group.getLastLoadTime()) {
			Map tmp = new HashMap(group);
			MapConfiguration config = new MapConfiguration(tmp);
			this.onUpdate(new SecutiryCompositeConfiguration(config));
		}
	}
	
	/**
	 * -Dprofile=yyy
	 * 
	 * @return
	 */
	private static String getProfile() {
		String profile = System.getProperty("profile", "");
		if (profile.trim().length() > 0) {
			profile = profile + ".";
		} else {
			profile = "";
		}
		return profile;
	}

	/**
	 * -Dapp.home.dir=xxxx
	 * 
	 * @return
	 */
	private static String getAppHomeDir() {
		String app$home$dir = System.getProperty(APP_HOME_DIR);
		if (app$home$dir == null) {
			app$home$dir = "";
		}
		return app$home$dir;
	}

	/**
	 * 配置文件sampleName
	 * 
	 * @param fileName
	 */
	public void setFileName(String fileName) {
		this._settingFileName = fileName;
	}

	public String getType() {
		return type;
	}

	/**
	 * 配置文件类型［properties/xml］,默认为xml
	 * 
	 * @param type
	 */
	public void setType(String type) {
		this.type = type;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public boolean isDelimiterParsingDisabled() {
		return delimiterParsingDisabled;
	}

	public void setDelimiterParsingDisabled(boolean delimiterParsingDisabled) {
		this.delimiterParsingDisabled = delimiterParsingDisabled;
	}

	@Override
	public final synchronized void onUpdate(Configuration config) {

		if (logger.isDebugEnabled()) {
			logger.debug("onUpdate() - start"); //$NON-NLS-1$
		}

		String oldStr = this.configToString(this.getConfig());

		String newStr = this.configToString(config);

		String old_ = MD5Util.md5Hex(oldStr);

		String new_ = MD5Util.md5Hex(newStr);
		/**
		 * 当前配置项没有变化
		 */
		if (old_.equals(new_)) {
			if (logger.isInfoEnabled()) {
				logger.info("onUpdate(当前配置项没有变化) - end"); //$NON-NLS-1$
			}
			return;
		}

		/**
		 * 配置变化
		 */
		build(config);
		/**
		 * 更新老配置
		 */
		this.delegate = config;

		fireConfigChanged();

		if (logger.isInfoEnabled()) {
			logger.info("String old={}", oldStr); //$NON-NLS-1$
			logger.info("String new={}", newStr);
		}
	}

	protected String configToString(Configuration config) {
		if (config == null) {
			return "";
		}
		Iterator<String> it = config.getKeys();
		if (it == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		while (it.hasNext()) {
			String key = it.next();
			sb.append(key + "=" + config.getString(key) + "\n");
		}
		return sb.toString();
	}

	/**
	 * 构造新的对象：datasource、memcache
	 * 
	 * @param config
	 */
	protected void build(Configuration config) {
	}

	private List<IConfigChangeListener> listeners = new ArrayList<IConfigChangeListener>();

	public synchronized void addChangeListener(IConfigChangeListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	private DynamicConfig root;

	public DynamicConfig getRoot() {
		return root;
	}

	public void setRoot(DynamicConfig root) {
		this.root = root;
	}

	/**
	 * 事件
	 */
	private void fireConfigChanged() {
		int size = listeners.size();
		for (int i = 0; i < size; i++) {
			listeners.get(i).configChanged();
		}
	}

	private boolean _throwExceptionOnMissing = false;

	public void setThrowExceptionOnMissing(boolean value) {
		_throwExceptionOnMissing = value;
	}

	public Configuration subset(String prefix) {
		return delegate.subset(prefix);
	}

	public boolean isEmpty() {
		return delegate.isEmpty();
	}

	public boolean containsKey(String key) {
		return delegate.containsKey(key);
	}

	public void addProperty(String key, Object value) {
		delegate.addProperty(key, value);
	}

	public void setProperty(String key, Object value) {
		delegate.setProperty(key, value);
	}

	public void clearProperty(String key) {
		delegate.clearProperty(key);
	}

	public void clear() {
		delegate.clear();
	}

	public Object getProperty(String key) {
		return delegate.getProperty(key);
	}

	public Iterator getKeys(String prefix) {
		return delegate.getKeys(prefix);
	}

	public Iterator getKeys() {
		return delegate.getKeys();
	}

	public Properties getProperties(String key) {
		return delegate.getProperties(key);
	}

	public boolean getBoolean(String key) {
		return delegate.getBoolean(key);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		return delegate.getBoolean(key, defaultValue);
	}

	public Boolean getBoolean(String key, Boolean defaultValue) {
		return delegate.getBoolean(key, defaultValue);
	}

	public byte getByte(String key) {
		return delegate.getByte(key);
	}

	public byte getByte(String key, byte defaultValue) {
		return delegate.getByte(key, defaultValue);
	}

	public Byte getByte(String key, Byte defaultValue) {
		return delegate.getByte(key, defaultValue);
	}

	public double getDouble(String key) {
		return delegate.getDouble(key);
	}

	public double getDouble(String key, double defaultValue) {
		return delegate.getDouble(key, defaultValue);
	}

	public Double getDouble(String key, Double defaultValue) {
		return delegate.getDouble(key, defaultValue);
	}

	public float getFloat(String key) {
		return delegate.getFloat(key);
	}

	public float getFloat(String key, float defaultValue) {
		return delegate.getFloat(key, defaultValue);
	}

	public Float getFloat(String key, Float defaultValue) {
		return delegate.getFloat(key, defaultValue);
	}

	public int getInt(String key) {
		return delegate.getInt(key);
	}

	public int getInt(String key, int defaultValue) {
		return delegate.getInt(key, defaultValue);
	}

	public Integer getInteger(String key, Integer defaultValue) {
		return delegate.getInteger(key, defaultValue);
	}

	public long getLong(String key) {
		return delegate.getLong(key);
	}

	public long getLong(String key, long defaultValue) {
		return delegate.getLong(key, defaultValue);
	}

	public Long getLong(String key, Long defaultValue) {
		return delegate.getLong(key, defaultValue);
	}

	public short getShort(String key) {
		return delegate.getShort(key);
	}

	public short getShort(String key, short defaultValue) {
		return delegate.getShort(key, defaultValue);
	}

	public Short getShort(String key, Short defaultValue) {
		return delegate.getShort(key, defaultValue);
	}

	public BigDecimal getBigDecimal(String key) {
		return delegate.getBigDecimal(key);
	}

	public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) {
		return delegate.getBigDecimal(key, defaultValue);
	}

	public BigInteger getBigInteger(String key) {
		return delegate.getBigInteger(key);
	}

	public BigInteger getBigInteger(String key, BigInteger defaultValue) {
		return delegate.getBigInteger(key, defaultValue);
	}

	public String getString(String key) {
		return delegate.getString(key);
	}

	public String getString(String key, String defaultValue) {
		return delegate.getString(key, defaultValue);
	}

	public String[] getStringArray(String key) {
		return delegate.getStringArray(key);
	}

	public List getList(String key) {
		return delegate.getList(key);
	}

	public List getList(String key, List defaultValue) {
		return delegate.getList(key, defaultValue);
	}

	protected Configuration getConfig() {
		return delegate;
	}
}
