var express = require("express");
var fs = require("fs"), json;
var file = __dirname + '/' + 'response.txt';
app = express();
app.get('/', function (req, res) {
	var response = fs.readFileSync(file, 'utf8');
	res.status(200).send(response);
});
var port = 8000;
app.listen(port, function() {
    console.log("node server is up");
});
