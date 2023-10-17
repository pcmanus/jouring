#!/bin/sh
gcc -fPIC -luring -shared -march=native -g -o libjouring.so libjouring.c 
