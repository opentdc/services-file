/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.util.LanguageCode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class AbstractFileServiceProvider<T> {
	protected static final String SEED_FN = "/seed.json";
	protected static final String DATA_FN = "/data.json";
	protected static final Logger logger = Logger.getLogger(AbstractFileServiceProvider.class.getName());
	
	// instance variables
	private File dataF = null;
	private File seedF = null;
	protected boolean isPersistent = true;
	protected LanguageCode lang = null;

	public AbstractFileServiceProvider(
		ServletContext context,
		String prefix
	) throws IOException {
		String _value = context.getInitParameter("isPersistent");  // true (= file) or false (= transient), default is true
		if (_value != null) {
			isPersistent = new Boolean(_value).booleanValue();
		}
		if (isPersistent == true) {
			logger.info("FileServiceProvider is persistent");
			if (dataF == null) {
				dataF = new File(context.getRealPath("/" + prefix + DATA_FN));
			}
			if (seedF == null) {
				seedF = new File(context.getRealPath("/" + prefix + SEED_FN));
			}
		}
		else {
			logger.info("FileServiceProvider is transient");
		}
	}
	
	protected List<T> importJson(
			File f)  {
		if (isPersistent == false) {
			logger.info("importJson(): not importing data (transient)");
			return new ArrayList<T>();
		}
		logger.info("importJson(" + f.getName() + "): importing data");
		if (!f.exists()) {
			logger.warning("importJson(" + f.getName()
					+ "): file does not exist.");
			return new ArrayList<T>();
		}
		if (!f.canRead()) {
			logger.warning("importJson(" + f.getName()
					+ "): file is not readable");
			return new ArrayList<T>();
		}
		logger.info("importJson(" + f.getName() + "): can read the file.");

		Reader _reader = null;
		ArrayList<T> _list = null;
		try {
			_reader = new InputStreamReader(new FileInputStream(f));
			Gson _gson = new GsonBuilder().create();
			Type _collectionType = new TypeToken<ArrayList<T>>() {}.getType();
			_list = _gson.fromJson(_reader, _collectionType);
			logger.info("importJson(" + f.getName() + "): json data converted to type " + _collectionType.toString());
		} catch (FileNotFoundException e1) {
			logger.severe("importJson(" + f.getName()
					+ "): file does not exist (2).");
			e1.printStackTrace();
		} finally {
			try {
				if (_reader != null) {
					_reader.close();
				}
			} catch (IOException e) {
				logger.severe("importJson(" + f.getName()
						+ "): IOException when closing the reader.");
				e.printStackTrace();
			}
		}
		logger.info("importJson(" + f.getName() + "): " + _list.size()
				+ " objects imported.");
		return _list;
	}

	protected List<T> importJson() throws IOException {
		if (isPersistent == false) {
			logger.info("importJson(): not importing data (transient)");
			return new ArrayList<T>();
		}
		List<T> _objects = null;
		
		// read the data file
		// either read persistent data from DATA_FN
		// or seed data from SEED_FN if no persistent data exists
		if (dataF.exists()) {
			logger.info("persistent data in file " + dataF.getName()
					+ " exists.");
			_objects = importJson(dataF);
		} else { // seeding the data
			logger.info("persistent data in file " + dataF.getName()
					+ " is missing -> trying to seed from " + seedF.getName());
			// importing the seed data
			_objects = importJson(seedF);
			// create the persistent data if it did not exist
			if (isPersistent == true) {
				try {
					dataF.createNewFile();					
					exportJson(_objects);
				} 
				catch (IOException _e) {
					logger.severe("importJson(): IO exception when creating file "
							+ dataF.getName());
					_e.printStackTrace();
				}
			}
		}

		logger.info("importJson(): imported " + _objects.size()
				+ " objects");
		return _objects;
	}

	protected void exportJson(Collection<T> objects) {
		if (isPersistent == false) {
			logger.info("exportJson(): not exporting data (transient)");
			return;
		}
		logger.info("exportJson(" + dataF.getName() + "): exporting objects in json format");

		Writer _writer = null;
		try {
			_writer = new OutputStreamWriter(new FileOutputStream(dataF));
			Gson _gson = new GsonBuilder().setPrettyPrinting().create();
			_gson.toJson(objects, _writer);
		} catch (FileNotFoundException e) {
			logger.severe("exportJson(" + dataF.getName() + "): file not found.");
			e.printStackTrace();
		} finally {
			if (_writer != null) {
				try {
					_writer.close();
				} catch (IOException e) {
					logger.severe("exportJson(" + dataF.getName()
							+ "): IOException when closing the reader.");
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Sets the current language code.
	 * Resolution strategy:
	 * 1) per request: query parameter 'lang'
	 * 2) per user session: session parameter 'lang'
	 * 3) per service: HttpServletContext (init param 'language' in web.xml configuration)
	 * 4) default:  LanguageCode.getDefaultLanguageCode()
	 * @param langCode the language code
	 * @param context the servlet context
	 * @return the language code applied
	 */
	public LanguageCode setLanguageCode(
			String langCode, 
			ServletContext context) {
		// 1) per request (query) or 2) per user session:
		if (langCode != null) {
			try {
				lang = LanguageCode.valueOf(langCode);
			}
			catch (IllegalArgumentException _ex) {
				logger.warning("invalid LanguageCode <" + langCode + "> is ignored; applying defaults instead.");
			}
		} 
		// 3) per service in web.xml configuration (init param language)
		if (lang == null) {
			String _langCode = context.getInitParameter("language");
			try {
				lang = LanguageCode.valueOf(_langCode);
			}
			catch (IllegalArgumentException _ex) {
				logger.warning("invalid LanguageCode <" + langCode + "> in web.xml (init parameter language) is ignored; applying defaults instead.");
			}
		}
		// 4) default
		if (lang == null) {
			lang = LanguageCode.getDefaultLanguageCode();
		}
		logger.info("setLanguageCode(" + langCode + ", context) -> " + lang.toString());
		return lang;
	}
}
