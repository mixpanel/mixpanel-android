import BaseHTTPServer

HOST_NAME = ''
PORT_NUMBER = 8000

class request_handler(BaseHTTPServer.BaseHTTPRequestHandler):
    def do_GET(self):
        with open('response.txt', 'r') as f:
            response = f.read()
        self.send_response(200)
        self.end_headers()
        self.wfile.write(response)

if __name__ == '__main__':
    server_class = BaseHTTPServer.HTTPServer
    httpd = server_class((HOST_NAME, PORT_NUMBER), request_handler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
