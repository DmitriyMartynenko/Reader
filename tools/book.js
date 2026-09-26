// Loads a built book (app/src/main/assets/books/<id>.book.json) for the check scripts.
const path = require('path');

const root = path.join(__dirname, '..');

function loadBook(id) {
  if (!id) {
    console.error('usage: node tools/<check>.js <book id> …');
    process.exit(1);
  }
  const book = require(path.join(root, 'app/src/main/assets/books', `${id}.book.json`));
  return { ...book, dir: path.join(root, 'books', id) };
}

module.exports = { loadBook };
