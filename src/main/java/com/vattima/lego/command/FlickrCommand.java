package com.vattima.lego.command;

import com.flickr4java.flickr.FlickrException;
import com.flickr4java.flickr.Response;
import com.flickr4java.flickr.Transport;
import com.flickr4java.flickr.collections.Collection;
import com.flickr4java.flickr.collections.CollectionsInterface;
import com.flickr4java.flickr.photosets.Photoset;
import com.flickr4java.flickr.photosets.Photosets;
import com.flickr4java.flickr.photosets.PhotosetsInterface;
import com.vattima.lego.imaging.LegoImagingException;
import com.vattima.lego.imaging.flickr.configuration.FlickrProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.bricklink.data.lego.dto.BricklinkItem;
import net.bricklink.data.lego.ibatis.mapper.BricklinkItemMapper;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@CommandLine.Command(name = "flickr", aliases = {"flickr"}, subcommands = {FlickrCommand.ListCollectionsCommand.class})
@Getter
@Setter
@Component
@RequiredArgsConstructor
public class FlickrCommand implements Runnable {
    private final PhotosetsInterface photosetsInterface;
    private final CollectionsInterface collectionsInterface;
    private final BricklinkItemMapper bricklinkItemMapper;
    private final Transport transportAPI;
    private final FlickrProperties.Secrets secrets;

    @Override
    public void run() {
        log.info("FlickrCommand");
    }

    @CommandLine.Command(name = "list-collections", aliases = {"-lc"}, description = "Lists all flickr Collections")
    static class ListCollectionsCommand implements Runnable {

        @CommandLine.ParentCommand
        FlickrCommand parent;

        @Override
        public void run() {
            log.info("ListCollectionsCommand");
            try {
                final LegoCollectionHolder legoCollection = new LegoCollectionHolder();
                Pattern legoPhotosetTitlePattern = Pattern.compile("^([0-9]{3,4}-?[0-9]?)\\s-\\s(.+)$");

                List<Collection> collections = parent.collectionsInterface.getTree(null, null);
                collections.stream()
                           .filter(c -> c.getTitle()
                                         .equals("Lego Sets For Sale"))
                           .forEach(c -> {
                               log.info("Collection [{} - {}]", c.getId(), c.getTitle());
                               legoCollection.accept(c);
                           });

                log.info("Lego Collection [{} - {}]", legoCollection.get()
                                                                    .getId(), legoCollection.get()
                                                                                            .getTitle());

                List<Photoset> legoPhotosets = new ArrayList<>();
                Photosets photosets = parent.photosetsInterface.getList(null);
                photosets.getPhotosets()
                         .stream()
                         .filter(p -> {
                             boolean filter = false;
                             Matcher matcher = legoPhotosetTitlePattern.matcher(p.getTitle());
                             if (matcher.matches()) {
                                 String blItemNumber = matcher.group(1);
                                 Optional<BricklinkItem> bricklinkItem = parent.bricklinkItemMapper.getBricklinkItemForBricklinkItemNumber(blItemNumber);
                                 filter = bricklinkItem.isPresent();
                             }
                             return filter;
                         })
                         .forEach(p -> {
                             legoPhotosets.add(p);
                             log.info("Photoset [{} - {}]", p.getId(), p.getTitle());
                         });
                flickrCollectionEditSets(legoCollection.get().getId(), legoPhotosets);
            } catch (FlickrException e) {
                throw new LegoImagingException(e);
            }
        }

        private void flickrCollectionEditSets(String collectionId, List<Photoset> photosets) throws FlickrException {
            Map<String, Object> parameters = new HashMap();
            parameters.put("method", "flickr.collections.editSets");
            parameters.put("collection_id", collectionId);
            parameters.put("photoset_ids", photosets.stream()
                                                    .map(Photoset::getId)
                                                    .collect(Collectors.joining(",")));
            Response response = parent.transportAPI.post(parent.transportAPI.getPath(), parameters, parent.secrets.getKey(), parent.secrets.getSecret());
            if (response.isError()) {
                throw new FlickrException(response.getErrorCode(), response.getErrorMessage());
            }
        }
    }

    private static class LegoCollectionHolder implements Consumer<Collection>, Supplier<Collection> {
        private Collection legoCollection;

        @Override
        public void accept(Collection collection) {
            legoCollection = collection;
        }

        @Override
        public Collection get() {
            return legoCollection;
        }
    }
}
