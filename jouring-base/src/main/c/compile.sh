#!/bin/sh
gcc -D_GNU_SOURCE -fPIC -luring -shared -march=native -g -o libjouring.so libjouring.c 
