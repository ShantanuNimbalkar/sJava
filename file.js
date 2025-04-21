var ignored = require('./ignored');

module.exports = function(x) {
  if (x < 0)
    x = -x;
    console.log(x);
    var y = x;
    
  return x * x;
};
