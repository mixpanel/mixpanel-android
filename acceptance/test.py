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
        text_size_change_response = '{"notifications":[],"surveys":[],"variants":[{"tweaks":[],"actions":[{"property":{"classname":"android.widget.TextView","set":{"parameters":[{"type":"java.lang.Integer"}],"selector":"setTextColor"},"name":"textColor","get":{"result":{"type":"android.content.res.ColorStateList"},"parameters":[],"selector":"getTextColors"}},"path":[{"index":0,"prefix":"shortest","id":16908290},{"index":0,"view_class":"android.widget.LinearLayout"},{"index":0,"view_class":"android.widget.Button"}],"args":[[4278253824,"java.lang.Integer"]],"change_type":"property","name":"c170"},{"property":{"classname":"android.view.View","set":{"parameters":[{"type":"java.lang.Integer"}],"selector":"setVisibility"},"name":"hidden","get":{"result":{"type":"java.lang.Integer"},"parameters":[],"selector":"getVisibility"}},"path":[{"index":0,"prefix":"shortest","id":16908290},{"index":0,"view_class":"android.widget.LinearLayout"},{"index":0,"mp_id_name":"edit_email_address"}],"args":[[4,"java.lang.Integer"]],"change_type":"property","name":"c272"},{"property":{"classname":"android.view.View","set":{"parameters":[{"type":"java.lang.Float"}],"selector":"setAlpha"},"name":"alpha","get":{"result":{"type":"java.lang.Float"},"parameters":[],"selector":"getAlpha"}},"path":[{"index":0,"prefix":"shortest","id":16908290},{"index":0,"view_class":"android.widget.LinearLayout"},{"index":1,"view_class":"android.widget.Button"}],"args":[[0.32,"java.lang.Float"]],"change_type":"property","name":"c323"}],"id":8974,"experiment_id":4290}]}'

        f = open('response.txt', 'w')
        f.write(text_size_change_response)
        f.close()
        self._launch_app()
        self.assertEquals(self.driver.find_element_by_id('send_to_mixpanel').get_attribute('textSize'), '108.0')

if __name__ == '__main__':
	unittest.main()
