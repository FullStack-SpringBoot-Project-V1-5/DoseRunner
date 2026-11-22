# libroute.so — A* in C, loaded from Java with FFM (SymbolLookup.libraryLookup).
#   make          -> libroute.so
#   make test     -> builds and runs test_route against the shared library
#   make clean
CC      ?= cc
CFLAGS  ?= -O2
CFLAGS  += -std=c11 -Wall -Wextra -Wpedantic -Werror -fPIC -fvisibility=hidden
LDFLAGS += -shared

.PHONY: all test clean

all: libroute.so

libroute.so: libroute.c libroute.h
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ libroute.c

test_route: test_route.c libroute.h libroute.so
	$(CC) -std=c11 -Wall -Wextra -O1 -o $@ test_route.c -L. -lroute -Wl,-rpath,'$$ORIGIN'

test: test_route
	./test_route

clean:
	rm -f libroute.so test_route
