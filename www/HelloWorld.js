var exec = require('cordova/exec');

exports.aduRead = function (arg0, success, error) {
    exec(success, error, 'HelloWorld', 'aduRead', [arg0]);
};

exports.aduWrite = function (arg0, success, error) {
    exec(success, error, 'HelloWorld', 'aduWrite', [arg0]);
};

exports.closeAduDevice = function (arg0, success, error) {
    exec(success, error, 'HelloWorld', 'closeAduDevice', [arg0]);
};
