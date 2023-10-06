Supposedly allow to compare read performance using various methods.

Simplest way to build/use is probably to build a jar with dependencies with:
```sh
mvn clean package assembly:single
```

Then the `jfio` script in this repository starts the built jar. That script expects one or more file as parameters and
does a number of reads on those files. There is a few options, and you can run `./jfio -h` to get a full description,
but something like:
```sh
./jfio -e mmap_native <some file(s)>
```
will mmap the files and do reads through that, and by default this runs 1M reads at random offset, each read being 64k
(max; the generated offset are currently not aligned so some read will be smaller if the offset is near the end of the
file), and it uses `Runtime.getRuntime().availableProcessors()` threads. But you can use different options and something
like:
```sh
./jfio -e iouring_async -b 512 -r 10000
```
would do only 10k reads of 512 bytes using io_uring (as provided by the jascyncfio lib).
