Attempts to help comparing read performance using various methods.

To build/use:
1. make sure to compile the small native library in `jouring-base/src/main/c`; there is a one-liner `compile.sh`
  script if you want.
2. run `mvn install` from the base directory.
3. then you should be able to use the `./jfio` helper script from this directly (which manually links the depended on
   jars from the local `.m2` repository, so the `mvn install` is needed; yes, this could be cleaner).

Said `jfio` script expects one or more file as parameters and runs a number of reads on those files. There is a few
options, and you can run `./jfio -h` to get a full description, but something like:
```sh
./jfio -e mmap_native <some file(s)>
```
will mmap the files and do reads through that, and by default this runs 5M reads at random offsets, each read being 64k
(max; the generated offset are currently not aligned so some read will be smaller if the offset is near the end of the
file), and it uses `Runtime.getRuntime().availableProcessors()` threads. But you can use different options and something
like:
```sh
./jfio -e iouring_async -b 4 -r 10000
```
would do only 10k reads of 4kb using `io_uring`.
