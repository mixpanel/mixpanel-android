import unittest
from selenium import webdriver

class FindElementTest(unittest.TestCase):
    def _launch_app(self):
        desired_capabilities = {'aut': 'com.mixpanel.example.hello:1.0'}
        self.driver = webdriver.Remote(
            desired_capabilities=desired_capabilities
        )
        self.driver.implicitly_wait(30)

    def tearDown(self):
        open('response.txt', 'w').close()
        self.driver.quit()

    def test_change_text_size(self):
        text_size_change_response = '{"variants":[{"experiment_id":3585,"id":7647,"tweaks":[],"actions":[{"path":[{"id":16908290,"index":0,"prefix":"shortest"},{"view_class":"android.widget.LinearLayout","index":0},{"index":0,"mp_id_name":"edit_first_name"}],"args":[[4289362242,"java.lang.Integer"]],"property":{"get":{"selector":"getTextColors","result":{"type":"android.content.res.ColorStateList"},"parameters":[]},"set":{"selector":"setTextColor","parameters":[{"type":"java.lang.Integer"}]},"name":"textColor","classname":"android.widget.TextView"},"name":"c117"},{"path":[{"id":16908290,"index":0,"prefix":"shortest"},{"view_class":"android.widget.LinearLayout","index":0},{"index":0,"mp_id_name":"edit_first_name"}],"args":[[2,"java.lang.Integer"],[36,"java.lang.Float"]],"property":{"get":{"selector":"getTextSize","result":{"type":"java.lang.Float"},"parameters":[]},"set":{"selector":"setTextSize","parameters":[{"type":"java.lang.Integer"},{"type":"java.lang.Float"}]},"name":"fontSize","classname":"android.widget.TextView"},"name":"c131"}]}],"surveys":[],"notifications":[]}'
        f = open('response.txt', 'w')
        f.write(text_size_change_response)
        f.close()
        self._launch_app()
        self.assertEquals(self.driver.find_element_by_id('edit_first_name').get_attribute('textSize'), '108.0')

if __name__ == '__main__':
	unittest.main()
