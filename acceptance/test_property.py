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

    def test_layout_change_basic(self):
        layout_change_response = '{"notifications":[],"surveys":[],"variants":[{"tweaks":[],"actions":[{"args":[{"view_id_name":"send_to_mixpanel","verb":3,"anchor_id_name":"0"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"send_to_mixpanel","verb":11,"anchor_id_name":"-1"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"send_to_mixpanel","verb":3,"anchor_id_name":"edit_first_name"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"}],"id":8990,"experiment_id":4302}]}'

        f = open('response.txt', 'w')
        f.write(layout_change_response)
        f.close()
        self._launch_app()
        self.assertTrue(self.driver.find_element_by_id('send_to_mixpanel').location['y'] < self.driver.find_element_by_id('edit_email_address').location['y'])
        self.assertTrue(self.driver.find_element_by_id('send_to_mixpanel').location['x'] > self.driver.find_element_by_id('edit_email_address').location['x'])
        self.assertEquals(self.driver.find_element_by_id('send_revenue').location['y'], self.driver.find_element_by_id('edit_email_address').location['y'])

if __name__ == '__main__':
	unittest.main()
