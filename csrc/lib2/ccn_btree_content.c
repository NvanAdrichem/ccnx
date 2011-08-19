/**
 * B-tree for indexing ccnx content objects
 */
/* (Will be) Part of the CCNx C Library.
 *
 * Copyright (C) 2011 Palo Alto Research Center, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. You should have received
 * a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 51 Franklin Street,
 * Fifth Floor, Boston, MA 02110-1301 USA.
 */
 
#include <ccn/btree_content.h>
#include <ccn/ccn.h>
#include <ccn/uri.h>

/**
 *  Append Components from a ccnb-encoded Name to a flatname 
 *  @param dst is the destination, which should hold a ccnb-encoded Name
 *  @param ccnb points to first byte of Name
 *  @param size is the number of bytes in ccnb
 *  @param index is the number of components at the front of flatname to skip
 *  @param count is the maximum number of componebts to append, or -1 for all
 *  @returns number of appended components, or -1 if there is an error.
 */
int
ccn_flatname_append_from_ccnb(struct ccn_charbuf *dst,
                                  const unsigned char *ccnb, size_t size,
                                  int index, int count)
{
    return(-1);
}

/**
 *  Convert a ccnb-encoded Name to a flatname
 *  @returns number of components, or -1 if there is an error.
 */
int
ccn_flatname_from_ccnb(struct ccn_charbuf *dst,
                           const unsigned char *ccnb, size_t size)
{
    dst->length = 0;
    return(ccn_flatname_append_from_ccnb(dst, ccnb, size, 0, -1));
}

/** Get delimiter size from return value of ccn_flatname_next_comp */
#define CCNFLATDELIMSZ(res) ((res) & 3)
/** Get data size from return value of ccn_flatname_next_comp */
#define CCNFLATDATASZ(res) ((res) >> 2)
/** Get total delimited size from return value of ccn_flatname_next_comp */
#define CCNFLATSKIP(res) (CCNFLATDELIMSZ(res) + CCNFLATDATASZ(res))
/**
 * Parse the component delimiter from the start of a flatname
 *
 * The delimiter size is limited to 3 bytes.
 * @returns -1 for error, 0 nothing left, or compsize * 4 + delimsize
 */
static int
ccn_flatname_next_comp(const unsigned char *flatname, size_t size)
{
    unsigned i, l, m;
    
    if (size == 0)
        return(0);
    if (flatname[0] == 0x80)
        return(-1); /* Must use min number of bytes. */
    m = (size < 3) ? size : 3;
    for (i = 0, l = 0; i < m && (flatname[i] & 0x80) != 0; i++)
        l = (l | (flatname[i] & 0x7F)) << 7;
    if (i >= m)
        return(-1);
    l |= flatname[i++];
    if (i + l > size)
        return(-1);
    return(l * 4 + i);
}

/**
 * Worker bee for walking a flatname
 */
static int
ccn_flatname_enumerate_comps(const unsigned char *flatname, size_t size,
                             int (*func)(void *data, int i, const unsigned char *cp, size_t cs),
                             void *data)
{
    int ans = 0;
    int i;
    int res;
    size_t k = 0;
    size_t l = 0;
    
    for (i = 0; i < size; i += k + l) {
        res = ccn_flatname_next_comp(flatname + i, size - i);
        if (res <= 0)
            return(-1);
        k = CCNFLATDELIMSZ(res);
        l = CCNFLATDATASZ(res);
        if (func(data, ans, flatname + i + k, l) < 0)
            return(-1);
        ans++;
    }
    if (i != size)
        return(-1);
    return(ans);
}

/** data for append_a_component */
struct append_a_component_param {
    struct ccn_charbuf *dst;
    int index;
    int count;
};

/** Helper for ccn_name_append_flatname */
static int
append_a_component(void *data, int i, const unsigned char *cp, size_t cs)
{
    struct append_a_component_param *p = data;
    int res = 0;
    if (i >= p->index && p->count != 0) {
        res = ccn_name_append(p->dst, cp, cs);
        p->count--;
    }
    return(res);
}

/**
 *  Append Components from a flatname to a ccnb-encoded Name
 *  @param dst is the destination, which should hold a ccnb-encoded Name
 *  @param flatname points to first byte of flatname
 *  @param size is the number of bytes in flatname
 *  @param index is the number of components at the front of flatname to skip
 *  @param count is the maximum number of componebts to append, or -1 for all
 *  @returns number of appended components, or -1 if there is an error.
 */
int
ccn_name_append_flatname(struct ccn_charbuf *dst,
                         const unsigned char *flatname, size_t size,
                         int index, int count)
{
    int res;
    struct append_a_component_param d;
    
    d.dst = dst;
    d.index = index;
    d.count = count;
    if (index < 0)
        return(-1);
    res = ccn_flatname_enumerate_comps(flatname, size, &append_a_component, &d);
    if (res >= index)
        return(res - index);
    return(-1);
}

/**
 * Like ccn_uri_append(), but accepts a flatname instead of ccnb
 */
int
ccn_uri_append_flatname(struct ccn_charbuf *uri,
                             const unsigned char *flatname, size_t size,
                             int includescheme)
{
    struct ccn_charbuf *ccnb = NULL;
    int res;
    
    ccnb = ccn_charbuf_create();
    if (ccnb == NULL)
        return(-1);
    res = ccn_name_init(ccnb);
    if (res < 0)
        goto Bail;
    res = ccn_name_append_flatname(ccnb, flatname, size, 0, -1);
    if (res < 0)
        goto Bail;
    res = ccn_uri_append(uri, ccnb->buf, ccnb->length, includescheme);
Bail:
    ccn_charbuf_destroy(&ccnb);
    return(res);
}

/** Helper for ccn_flatname_ncomps */
static int
nothing_to_to(void *data, int i, const unsigned char *cp, size_t cs)
{
    return(0);
}

/**
 * Get flatname component count
 * @returns the number of name components in the flatname, or -1 if the
 *          flatname is not well-formed
 */
int
ccn_flatname_ncomps(const unsigned char *flatname, size_t size)
{
    return(ccn_flatname_enumerate_comps(flatname, size, &nothing_to_to, NULL));
}
