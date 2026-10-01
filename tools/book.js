// Loads a built book for the check scripts: a library book from app/src/main/assets/books/,
// or a book of your own (my-books/<id>/) from dist/books/.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');

function loadBook(id) {
  if (!id) {
    console.error('usage: node tools/<check>.js <book id> …');
    process.exit(1);
  }
  const own = fs.existsSync(path.join(root, 'my-books', id));
  const file = own ? path.join(root, 'dist/books', `${id}.book.json`) : path.join(root, 'app/src/main/assets/books', `${id}.book.json`);
  const book = require(file);
  return { ...book, dir: path.join(root, own ? 'my-books' : 'books', id) };
}

module.exports = { loadBook };
