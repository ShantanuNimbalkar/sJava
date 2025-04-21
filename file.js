var ignored = require('./ignored');

module.exports = function(x) {
  if (x < 0)
    x = -x;
  x = -x;
    
  return x * x;
};
