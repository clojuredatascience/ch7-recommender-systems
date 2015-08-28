# Recommender systems

Example code for chapter seven, [Clojure for Data Science](https://www.packtpub.com/big-data-and-business-intelligence/clojure-data-science).

## Data

The data for this chapter is the ML-100k dataset of movie ratings provided by [GroupLens](http://grouplens.org/datasets/movielens/).

The dataset can be downloaded directly from [here](http://files.grouplens.org/datasets/movielens/ml-100k.zip).

## Instructions

### *nix and OS X

Run the following command-line script to download the data to the project's data directory:

```bash
# Downloads and unzips the data files into this project's data directory.
    
script/download-data.sh
```

### Windows / manual instructions

  1. Download the ml-100k.zip file linked above.
  2. Expand the contents of the file to a directory called data/ml-100k within this project's directory.

After following these steps files named ua.base and u.item should be inside the directory named data/ml-100k.

## Running examples

Examples can be run with:

```bash
# Replace 7.1 with the example you want to run:

lein run -e 7.1
```
or open an interactive REPL with:

```bash
lein repl
```

## License

Copyright © 2015 Henry Garner

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
