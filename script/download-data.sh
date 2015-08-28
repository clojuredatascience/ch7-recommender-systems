#!/bin/bash

tmp_dir="/tmp/movielens"
download_file=${tmp_dir}/ml-100k.zip
script_dir=$(cd -P -- "$(dirname -- "$0")" && pwd -P)
data_url="http://files.grouplens.org/datasets/movielens/ml-100k.zip"
data_dir="${script_dir}/../data"

mkdir -p "${data_dir}"

if [ ! -e "${data_dir}/ml-100k" ]; then
    mkdir -p "${tmp_dir}"
    echo "Downloading ${data_url}..."
    if [ $(curl -s --head -w %{http_code} $data_url -o /dev/null) -eq 200 ]; then
        curl $data_url -o "${download_file}"
        unzip "${download_file}" -d "${data_dir}"
    else
        echo "Couldn't download data. Perhaps it has moved? Consult http://wiki.clojuredatascience.com"
    fi    
fi
