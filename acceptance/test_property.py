import unittest
from selenium import webdriver

class AndroidTest(unittest.TestCase):
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
        text_size_change_response = '{"notifications":[],"surveys":[],"variants":[{"tweaks":[],"actions":[{"property":{"classname":"android.widget.TextView","set":{"parameters":[{"type":"java.lang.Integer"},{"type":"java.lang.Float"}],"selector":"setTextSize"},"name":"fontSize","get":{"result":{"type":"java.lang.Float"},"parameters":[],"selector":"getTextSize"}},"path":[{"index":0,"prefix":"shortest","id":16908290},{"index":0,"view_class":"android.widget.RelativeLayout"},{"index":0,"mp_id_name":"edit_first_name"}],"args":[[2,"java.lang.Integer"],[36,"java.lang.Float"]],"change_type":"property","name":"c65"}],"id":8990,"experiment_id":4302}]}'

        f = open('response.txt', 'w')
        f.write(text_size_change_response)
        f.close()
        self._launch_app()
        self.assertEquals(self.driver.find_element_by_id('edit_first_name').get_attribute('textSize'), '108.0')

if __name__ == '__main__':
	unittest.main()
