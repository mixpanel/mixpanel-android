import unittest
from selenium import webdriver

class AndroidTest(unittest.TestCase):
    def _launch_app(self, decide_message):
        f = open('response.txt', 'w')
        f.write(decide_message)
        f.close()

        desired_capabilities = {'aut': 'com.mixpanel.example.hello:1.0'}
        self.driver = webdriver.Remote(
            desired_capabilities=desired_capabilities
        )
        self.driver.implicitly_wait(30)

    def tearDown(self):
        open('response.txt', 'w').close()
        self.driver.quit()

    def test_layout_change_basic(self):
        decide_message = '{"notifications":[],"surveys":[],"variants":[{"tweaks":[],"actions":[{"args":[{"view_id_name":"send_to_mixpanel","verb":3,"anchor_id_name":"0"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"send_to_mixpanel","verb":11,"anchor_id_name":"-1"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"send_to_mixpanel","verb":3,"anchor_id_name":"edit_first_name"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"}],"id":8990,"experiment_id":4302}]}'
        self._launch_app(decide_message)

        edit_email_address_location = self.driver.find_element_by_id('edit_email_address').location
        send_to_mixpanel_location = self.driver.find_element_by_id('send_to_mixpanel').location
        send_revenue_location = self.driver.find_element_by_id('send_revenue').location

        self.assertTrue(send_to_mixpanel_location['y'] < edit_email_address_location['y'])
        self.assertTrue(send_to_mixpanel_location['x'] > edit_email_address_location['x'])
        self.assertEquals(send_revenue_location['y'], edit_email_address_location['y'])

    def test_layout_circular_dependency(self):
        decide_message = '{"notifications":[],"surveys":[],"variants":[{"tweaks":[],"actions":[{"args":[{"view_id_name":"edit_email_address","verb":3,"anchor_id_name":"send_to_mixpanel"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"}],"id":8990,"experiment_id":4302}]}'
        self._launch_app(decide_message)

        self.assertTrue(self.driver.find_element_by_id('send_to_mixpanel').location['y'] > self.driver.find_element_by_id('edit_email_address').location['y'])

    def test_layout_massive_changes(self):
        decide_message = '{"notifications":[],"surveys":[],"variants":[{"tweaks":[],"actions":[{"args":[{"view_id_name":"send_to_mixpanel","verb":3,"anchor_id_name":"0"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"send_to_mixpanel","verb":11,"anchor_id_name":"-1"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"send_revenue","verb":3,"anchor_id_name":"edit_first_name"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"edit_email_address","verb":3,"anchor_id_name":"send_to_mixpanel"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"set_background_image","verb":10,"anchor_id_name":"-1"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"set_background_image","verb":12,"anchor_id_name":"-1"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"}],"id":8990,"experiment_id":4302}]}'
        self._launch_app(decide_message)

        edit_first_name_location = self.driver.find_element_by_id('edit_first_name').location
        edit_last_name_location = self.driver.find_element_by_id('edit_last_name').location
        edit_email_address_location = self.driver.find_element_by_id('edit_email_address').location
        send_to_mixpanel_location = self.driver.find_element_by_id('send_to_mixpanel').location
        send_to_mixpanel_size = self.driver.find_element_by_id('send_to_mixpanel').size
        send_revenue_location = self.driver.find_element_by_id('send_revenue').location
        set_background_image_location = self.driver.find_element_by_id('set_background_image').location
        set_background_image_size = self.driver.find_element_by_id('set_background_image').size

        self.assertEquals(send_to_mixpanel_location['y'], edit_first_name_location['y'])
        self.assertTrue(send_to_mixpanel_location['x'] > send_revenue_location['x'])
        self.assertEquals(edit_email_address_location['y'], edit_last_name_location['y'])
        self.assertEquals(set_background_image_location['x'], 0)
        self.assertTrue(set_background_image_location['y'] < edit_last_name_location['y'])
        self.assertTrue(set_background_image_size['width'] > send_to_mixpanel_size['width'])
        self.assertTrue(set_background_image_size['height'] > send_to_mixpanel_size['height'])

    def test_layout_absent_views(self):
        decide_message = '{"notifications":[],"surveys":[],"variants":[{"tweaks":[],"actions":[{"args":[{"view_id_name":"edit_email_address","verb":3,"anchor_id_name":"0"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"fake_view","verb":3,"anchor_id_name":"0"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"},{"args":[{"view_id_name":"edit_email_address","verb":3,"anchor_id_name":"fake_view"}],"name":"c155","path":[{"prefix":"shortest","index":0,"id":16908290},{"view_class":"android.widget.RelativeLayout","index":0}],"change_type":"layout"}],"id":8990,"experiment_id":4302}]}'
        self._launch_app(decide_message)

        self.assertEquals(self.driver.find_element_by_id('edit_email_address').location['y'], self.driver.find_element_by_id('edit_first_name').location['y'])

if __name__ == '__main__':
	unittest.main()
