import org.junit.Before
import org.junit.Test

/**
  This class tests functions.groovy located in the scripts directory of this repository.
*/
class functionsTest extends GroovyTestCase {
    GroovyShell shell
    Binding binding
    File functions

    @Before protected void setUp() {
        binding = new Binding()
        shell = new GroovyShell(binding)
        functions = new File(this.getClass().getResource('/functions.groovy').getFile())
        shell.evaluate(functions)
    }
    @Test public void test_functions_getObjectValue() {
        Map example = [key1: [subkey1: 'string']]
        assert 'string' == binding.getObjectValue.call(example, 'key1.subkey1', 'default')
        assert 'default' == binding.getObjectValue.call(example, 'key2.subkey1', 'default')
        assert 2 == binding.getObjectValue.call(example, 'key1.subkey1', 2)
    }
    @Test public void test_functions_getObjectValue_false_bug() {
        Map example = [key1: [subkey1: 'false']]
        assert false == binding.getObjectValue.call(example, 'key1.subkey1', false)
        assert false == binding.getObjectValue.call(example, 'key1.subkey1', true)
    }
    @Test public void test_functions_getObjectValue_type_bug() {
        Map example = [key1: [subkey1: 'string'],key2: ["a", "b"]]
        assert 'default' == binding.getObjectValue.call(example, 'key1', 'default')
        assert 'default' == binding.getObjectValue.call(example, 'key2', 'default')
    }
    @Test public void test_functions_setObjectValue_map_listmap() {
        Map example = ['somekey':[[:],[:]]]
        assert [:] == example['somekey'][1]
        binding.setObjectValue.call(example, 'somekey[1].foo', 'hello')
        assert ['foo': 'hello'] == example['somekey'][1]
    }
    @Test public void test_functions_setObjectValue_list_setitem() {
        List example = ['',['somekey':[[:],[:]]]]
        assert [[:], [:]] == example[1]['somekey']
        binding.setObjectValue.call(example, '[1].somekey', 'hello')
        assert 'hello' == example[1]['somekey']
    }
    @Test public void test_functions_setObjectValue_list_allitem() {
        List example = ['',['somekey':[[:],[:]]]]
        binding.setObjectValue.call(example, '[*].somekey', 'hello')
        assert [['somekey':'hello'], ['somekey':'hello']] == example
    }
    @Test public void test_functions_setObjectValue_map_maplist() {
        Map someobject = ['rootkey': [1, 2, 3]]
        binding.setObjectValue.call(someobject, 'rootkey[*]', 'groovy')
        assert ['rootkey': ['groovy', 'groovy', 'groovy']] == someobject
    }
    @Test public void test_functions_setObjectValue_list_write() {
        List someobject = ['a', 'b', 'c']
        binding.setObjectValue.call(someobject, '[1]', 3)
        assert ['a', 3, 'c'] == someobject
    }
    @Test public void test_functions_setObjectValue_list_createmap() {
        List someobject = ['a', 'b', 'c']
        binding.setObjectValue.call(someobject, '[2].hello', 'world')
        assert ['a', 'b', ['hello':'world']] == someobject
    }
    @Test public void test_functions_setObjectValue_map_faillist() {
        List someobject = ['a', 'b', 'c']
        binding.setObjectValue.call(someobject, 'hello.[2]', 'world')
        assert ['a', 'b', 'c'] == someobject
    }
    @Test public void test_functions_setObjectValue_list_failmap() {
        Map someobject = ['hello':'world']
        binding.setObjectValue.call(someobject, '[0].hello', 'world')
        assert ['hello':'world'] == someobject
    }
    @Test public void test_functions_setObjectValue_list_nestedmap() {
        Map someobject = ['people': [['name':'Jack'], ['name':'Jill']]]
        binding.setObjectValue.call(someobject, 'people[0].age', 15)
        assert ['name': 'Jack', 'age': 15] == someobject['people'][0]
    }
}
