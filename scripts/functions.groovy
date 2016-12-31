//functions used by sandscape.groovy and Sandscape plugins

import java.text.DateFormat
import java.util.Date
import java.util.logging.Level
import java.util.logging.Logger
import org.apache.commons.lang.exception.ExceptionUtils

/*
   Define available bindings.  The bindings had to be listed first in order for
   recursion to work within closures.
*/
downloadFile = null
getObjectValue = null
isUrlGood = null
logger = null
sandscapeErrorLevelLogger = null
sandscapeErrorLogger = null
sandscapeLevelLogger = null
sandscapeLogger = null
sandscapePluginErrorLogger = null
sandscapePluginLevelLogger = null
sandscapePluginLogger = null
setObjectValue = null

/*
Sandscape logging mechanisms which are used by scripts and Sandscape plugins.

USAGE:

For sandscape scripts,

    sandscapeLogger("This is an informational message.")
    sandscapeErrorLogger("A critical error has occurred.")

For sandscape plugins,

    sandscapePluginLogger("This is an informational message.")
    sandscapePluginErrorLogger("A critical error has occurred.")
*/
//sandscape logging mechanisms
logger = Logger.getLogger('sandscape')
sandscapeLevelLogger = { Level level, String message ->
    String now = DateFormat.getDateTimeInstance().format(new Date())
    "${now} ${message}".with {
        if(level == Level.SEVERE) {
            println it
        }
        logger.log(level, it)
    }
}
sandscapePluginLevelLogger = {Level level, String message ->
    //get the class name of the class which called this function
    String callerClass = Thread.currentThread().getStackTrace().findAll { it.getFileName() && it.getFileName().endsWith('.groovy') }[1].with { it = it.getFileName() - '.groovy' }
    sandscapeLevelLogger(level, "[${callerClass} sandscape plugin] ${message}")
}
//http://groovy-lang.org/closures.html#_left_currying
sandscapeErrorLogger = sandscapeLevelLogger.curry(Level.SEVERE)
sandscapeLogger = sandscapeLevelLogger.curry(Level.INFO)
sandscapePluginErrorLogger = sandscapePluginLevelLogger.curry(Level.SEVERE)
sandscapePluginLogger = sandscapePluginLevelLogger.curry(Level.INFO)

/*
Get an object from a `Map` or return any object from `defaultValue`.
Guarantees that what is returned is the same type as `defaultValue`.  This is
used to get optional keys from YAML or JSON files.

USAGE:

    Map example = [key1: [subkey1: 'string']]
    getObjectValue(example, 'key1.subkey1', 'some default')

PARAMETERS:

* `object` - A `Map` which was likely created from a YAML or JSON file.
* `key` - A `String` with keys and subkeys separated by periods which is used
  to search the `object` for a possible value.
* `defaultValue` - A default value and type that should be returned.

RETURNS:

Returns the value of the key or a `defaultValue` which is of the same type as
`defaultValue`.  This function has coercion behaviors which are not the same as
Groovy:

* If the `defaultValue` is an instance of `String` and the retrieved key is an
  instance of `Map`, then `defaultValue` is returned rather than converting it
  to a `String`.
* If the `defaultValue` is an instance of `String` and the retrieved key is an
  instance of `List`, then `defaultValue` is returned rather than converting it
  to a `String`.
* If the `defaultValue` is an instance of `Boolean`, the retrieved key is an
  instance of `String` and has a value of `false`, then `Boolean false` is
  returned.
*/
getObjectValue = { Map object, String key, Object defaultValue ->
    if(key.indexOf('.') >= 0) {
        String key1 = key.split('\\.', 2)[0]
        String key2 = key.split('\\.', 2)[1]
        if(object.get(key1) != null && object.get(key1) instanceof Map) {
            return getObjectValue(object.get(key1), key2, defaultValue)
        }
        else {
            return defaultValue
        }
    }

    //try returning the value casted as the same type as defaultValue
    try {
        if(object.get(key) != null) {
            if((defaultValue instanceof String) && ((object.get(key) instanceof Map) || (object.get(key) instanceof List))) {
                return defaultValue
            }
            else {
                if((defaultValue instanceof Boolean) && (object.get(key) == 'false')) {
                    return false
                }
                else {
                    return object.get(key).asType(defaultValue.getClass())
                }
            }
        }
    }
    catch(Exception e) {}

    //nothing worked so just return default value
    return defaultValue
}

setObjectValue = { Object object, String key, Object setValue ->
    if(!(object instanceof Map) && !(object instanceof List)) {
        sandscapeErrorLogger("setObjectValue - object is not a Map or List.  ${object.class}")
        return
    }
    try {
        String key1, key2, nextKey
        key1 = key2 = nextKey = null
        if(key.indexOf('.') >= 0) {
            key1 = key.split('\\.', 2)[0]
            key2 = key.split('\\.', 2)[1]
            //nextKey is used later to detect of a List (empty) or a Map.
            nextKey = (key2.split('\\.', 2)[0] - ~/\[(-?[0-9]*\*?)\]$/)
            //check for a list i.e. somekey[1]
            if(key1.matches(/.*\[-?[0-9]*\*?\]$/)) {
                def index
                Object nextObject = null
                (key1 =~ /(.*)\[(-?[0-9]*\*?)\]$/)[0].with {
                    key1 = it[1]
                    index = it[2]
                }
                if(index == '*') {
                    //if key1 is empty then treat it as a List else a Map
                    nextObject = (key1.isEmpty())? object : object[key1]
                    nextObject.each { item ->
                        if(nextKey.isEmpty() && !(item instanceof List)) {
                            item = []
                        }
                        else if(!(item instanceof Map)) {
                            item = [:]
                        }
                        setObjectValue(item, key2, setValue)
                    }
                }
                else {
                    index = index.toInteger()
                    //if key1 is empty then treat it as a List else a Map
                    nextObject = (key1.isEmpty())? object[index] : object[key1][index]
                    //force the next key to be a Map or List
                    if(nextKey.isEmpty() && !(nextObject instanceof List)) {
                        nextObject = []
                    }
                    else if(!(nextObject instanceof Map)) {
                        nextObject = [:]
                    }
                    setObjectValue(nextObject, key2, setValue)
                }
            }
            //TODO write else for key1 == 'somekey' as opposed to 'somekey[1]'
        }
        else {
            //end of the list
            if(key1.matches(/.*\[-?[0-9]*\*?\]$/)) {
                //TODO what if the last item matches this block but is not a Map?
                def index
                Object nextObject = null
                (key1 =~ /(.*)\[(-?[0-9]*\*?)\]$/)[0].with {
                    key1 = it[1]
                    index = it[2]
                }
                if(index == '*') {
                    //if key1 is empty then treat it as a List else a Map
                    nextObject = (key1.isEmpty())? object : object[key1]
                    nextObject.each { item ->
                        if(nextKey.isEmpty() && !(nextObject instanceof List)) {
                            item = []
                        }
                        else if(!(nextObject instanceof Map)) {
                            item = [:]
                        }
                        setObjectValue(item, key2, setValue)
                    }
                }
                else {
                    index = index.toInteger()
                    //if key1 is empty then treat it as a List else a Map
                    nextObject = (key1.isEmpty())? object[index] : object[key1][index]
                    //force the next key to be a Map or List
                    if(nextKey.isEmpty() && !(nextObject instanceof List)) {
                        nextObject = []
                    }
                    else if(!(nextObject instanceof Map)) {
                        nextObject = [:]
                    }
                    setObjectValue(nextObject, key2, setValue)
                }
            }
            else {
                object[key] = setValue
            }
        }
    }
    catch(Exception e) {
    }
    null
}

/*
This function tests the health of a URL HTTP status by calling the HTTP HEAD
method of the HTTP protocol.

USAGE:

isUrlGood("http://example.com")

PARAMETERS:

* `url` - A `String` which is the URL of a website.

RETURNS:

A `Boolean`, `true` if the website returns an HTTP 2XX status code and `false`
otherwise.
*/
isUrlGood = { String url ->
    int code = -1
    try {
        code = new URL(url).openConnection().with {
            requestMethod = 'HEAD'
            //override HTTP headers Java sends for security reasons (discovered via tcpdump to http://example.com)
            addRequestProperty "User-Agent", "Java"
            addRequestProperty "Connection", "close"
            addRequestProperty "Accept", "*/*"
            //no network connection has been made up until this point
            responseCode
            //network connection is immediately closed
        }
    }
    catch(MalformedURLException e) {
        sandscapeErrorLogger(ExceptionUtils.getStackTrace(e))
    }
    catch(Exception e) {}
    //2XX status is success - https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2
    //return true if HTTP 2XX status
    return ((int) code / 100) == 2
}

/*
Download a file to a local `fullpath`.  If the parent directories of the path
are missing then they are automatically created (similar to the Linux command
`mkdir -p`).

USAGE:

    downloadFile("http://example.com", "/tmp/foo/index.html").

PARAMETERS:

* `url` - A `String` which is a URL to a file on a website.
* `fullpath` - A `String` which is a full file path.  It is the destination of
  the downloaded file.

RETURNS:

A `Bolean`, `true` if downloading the file was a success or `false` if not.
*/
downloadFile = { String url, String fullpath ->
    try {
        new File(fullpath).with { file ->
            //make parent directories if they don't exist
            if(!file.getParentFile().exists()) {
                file.getParentFile().mkdirs()
            }
            file.newOutputStream().with { file_os ->
                file_os << new URL(url).openStream()
                file_os.close()
            }
        }
    }
    catch(Exception e) {
        sandscapeErrorLogger(ExceptionUtils.getStackTrace(e))
        return false
    }
    return true
}
